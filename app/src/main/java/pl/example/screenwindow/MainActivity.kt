package pl.example.screenwindow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import java.security.SecureRandom

/** Bez zewnętrznych bibliotek i bez przechowywania tokenów przechwytywania. */
class MainActivity : Activity() {
    private companion object {
        const val REQ_OVERLAY = 1
        const val REQ_CAPTURE = 2
        const val REQ_NOTIF = 3
        const val PREFS = "screenwindow_settings"
    }

    private lateinit var qualitySpinner: Spinner
    private lateinit var roomCodeView: TextView
    private var roomCode = ""
    private val playerBase = "https://screenwindow-stream.dam-mniamx.workers.dev/?room="
    private val qualityValues = floatArrayOf(0.40f, 0.60f, 0.80f)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 14, 24)
        window.navigationBarColor = Color.rgb(10, 14, 24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(30), dp(22), dp(22))
            setBackgroundColor(Color.rgb(13, 19, 32))
        }
        root.addView(TextView(this).apply {
            text = "▣  SCREEN WINDOW"
            textSize = 25f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.rgb(123, 224, 239))
        })
        root.addView(TextView(this).apply {
            text = "Podgląd ekranu w ruchomym oknie nad innymi aplikacjami.\n" +
                "Przeciągaj pasek okna, zmieniaj rozmiar + / − lub szczypaniem."
            textSize = 16f
            setTextColor(Color.rgb(227, 235, 245))
            setPadding(0, dp(20), 0, dp(20))
        })
        root.addView(TextView(this).apply {
            text = "Jakość podglądu"
            textSize = 15f
            setTextColor(Color.WHITE)
        })
        qualitySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Oszczędna 40%", "Zrównoważona 60%", "Dokładna 80%")
            )
            setBackgroundColor(Color.rgb(42, 56, 73))
            setPadding(dp(10), 0, 0, 0)
        }
        val previous = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("quality_index", 1)
        qualitySpinner.setSelection(previous.coerceIn(0, 2))
        root.addView(qualitySpinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { bottomMargin = dp(18) })

        root.addView(TextView(this).apply {
            text = "ODTWARZACZ ZDALNY"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        roomCodeView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(123, 224, 239))
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(roomCodeView)
        root.addView(button("📋  KOPIUJ LINK ODTWARZACZA", 0xFF34465D.toInt()) {
            if (roomCode.isBlank()) createRoomCode()
            val link = playerBase + roomCode
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ScreenWindow player", link))
            Toast.makeText(this, "Skopiowano link odtwarzacza", Toast.LENGTH_SHORT).show()
        })
        createRoomCode()

        root.addView(button("▶  URUCHOM PODGLĄD + TRANSMISJĘ", 0xFF156C7E.toInt()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("quality_index", qualitySpinner.selectedItemPosition).apply()
            startCaptureFlow()
        })
        root.addView(button("■  ZATRZYMAJ", 0xFF37435A.toInt()) {
            startService(Intent(this, MirrorService::class.java).setAction(MirrorService.ACTION_STOP))
        })
        root.addView(TextView(this).apply {
            text = "Android poprosi o zgodę na udostępnianie ekranu przy każdym uruchomieniu. " +
                "Możesz zakończyć przechwytywanie z pływającego okna lub powiadomienia."
            textSize = 13f
            setTextColor(Color.rgb(169, 188, 207))
            setPadding(0, dp(24), 0, 0)
        })
        setContentView(root)
    }

    private fun button(label: String, color: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        ).apply { bottomMargin = dp(12) }
    }

    private fun createRoomCode() {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        roomCode = bytes.joinToString("") { "%02x".format(it) }
        if (::roomCodeView.isInitialized) {
            roomCodeView.text = "Kod: $roomCode\nLink: $playerBase$roomCode"
        }
    }

    private fun startCaptureFlow() {
        if (roomCode.isBlank()) createRoomCode()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Zezwól na wyświetlanie nad innymi aplikacjami", Toast.LENGTH_LONG).show()
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        askCapturePermission()
    }

    private fun askCapturePermission() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14+: explicite prosimy o cały ekran (nie pojedynczą aplikację).
        val request = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(request, REQ_CAPTURE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            // Zgoda na powiadomienia nie jest warunkiem samego MediaProjection.
            askCapturePermission()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> if (Settings.canDrawOverlays(this)) startCaptureFlow()
            REQ_CAPTURE -> {
                if (resultCode == RESULT_OK && data != null) {
                    val selected = qualitySpinner.selectedItemPosition.coerceIn(0, 2)
                    val serviceIntent = Intent(this, MirrorService::class.java)
                        .putExtra(MirrorService.EXTRA_CODE, resultCode)
                        .putExtra(MirrorService.EXTRA_DATA, data)
                        .putExtra(MirrorService.EXTRA_QUALITY, qualityValues[selected])\n                        .putExtra(MirrorService.EXTRA_ROOM, roomCode)
                    try {
                        startForegroundService(serviceIntent)
                        moveTaskToBack(true)
                    } catch (e: RuntimeException) {
                        Toast.makeText(this, "Nie udało się uruchomić usługi: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Anulowano udostępnianie ekranu", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
