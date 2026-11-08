package pl.example.screenwindow

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * Ekran przechwytywany jedną sesją MediaProjection, prezentowany przez TextureView.
 * Android 14+ zabrania powtórnego createVirtualDisplay dla tego samego tokenu;
 * orientację i rozmiar przechwytywanego obrazu zmieniamy przez VirtualDisplay.resize().
 */
class MirrorService : Service() {
    companion object {
        const val ACTION_STOP = "pl.example.screenwindow.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_QUALITY = "quality"
        private const val CHANNEL_ID = "mirror"
        private const val NOTIF_ID = 1
        private const val TAG = "ScreenWindow"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var callback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var previewSurface: Surface? = null
    private var textureView: TextureView? = null
    private var overlay: FrameLayout? = null
    private lateinit var windowManager: WindowManager
    private lateinit var layout: WindowManager.LayoutParams
    private var quality = 0.6f
    private var capturedWidth = 2
    private var capturedHeight = 2
    private var screenDensityDpi = 160
    private var captureStarted = false
    private var stopping = false
    private var barHeight = 0
    private var minimized = false
    private var expandedX = 0
    private var expandedY = 0
    private var expandedWidth = 0
    private var expandedHeight = 0

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null || projection != null || stopping) return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        quality = intent.getFloatExtra(EXTRA_QUALITY, 0.6f).coerceIn(0.3f, 0.9f)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenDensityDpi = resources.displayMetrics.densityDpi
        val size = getDisplaySize()
        updateCaptureDimensions(size.x, size.y)

        try {
            // MediaProjection wymaga aktywnej usługi pierwszoplanowej PRZED getMediaProjection().
            startAsForeground()
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newProjection = manager.getMediaProjection(resultCode, resultData)
            projection = newProjection
            callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { stopSelf() }
                }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    handler.post { resizeCapture(width, height) }
                }
            }.also { newProjection.registerCallback(it, handler) }
            showOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection start failed", e)
            Toast.makeText(this, "Podgląd nie wystartował: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        // Token jest jednorazowy, po ubiciu procesu Android nie może wznawiać sesji sam.
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Udostępnianie ekranu", NotificationManager.IMPORTANCE_LOW
        ))
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val launchIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Window: podgląd włączony")
            .setContentText("Dotknij, by otworzyć ustawienia, lub wybierz Zatrzymaj")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Zatrzymaj", stopIntent
            ).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun getDisplaySize(): Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds.let { Point(it.width(), it.height()) }
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(it)
            }
        }
    }

    private fun updateCaptureDimensions(sourceWidth: Int, sourceHeight: Int) {
        capturedWidth = ((sourceWidth * quality).toInt() / 2 * 2).coerceAtLeast(2)
        capturedHeight = ((sourceHeight * quality).toInt() / 2 * 2).coerceAtLeast(2)
    }

    private fun resizeCapture(width: Int, height: Int) {
        if (stopping || width <= 0 || height <= 0) return
        val oldWidth = capturedWidth
        val oldHeight = capturedHeight
        updateCaptureDimensions(width, height)
        if (oldWidth == capturedWidth && oldHeight == capturedHeight) return
        try {
            textureView?.surfaceTexture?.setDefaultBufferSize(capturedWidth, capturedHeight)
            virtualDisplay?.resize(capturedWidth, capturedHeight, screenDensityDpi)
            virtualDisplay?.setSurface(previewSurface)
            updateWindowSize(layout.width)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Resize failed", e)
        }
    }

    // Callback onCapturedContentResize istnieje od API 34. Starsze telefony dostają
    // zmiany orientacji poprzez Service.onConfigurationChanged().
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::windowManager.isInitialized && !stopping) {
            handler.post {
                val size = getDisplaySize()
                resizeCapture(size.x, size.y)
                if (::layout.isInitialized) clampWindowPosition()
            }
        }
    }

    private fun updateWindowSize(requestedWidth: Int) {
        if (!::layout.isInitialized || overlay == null) return
        val screen = getDisplaySize()
        val minW = dp(185).coerceAtMost(screen.x)
        val margin = dp(10)
        val maximumHeight = (screen.y - barHeight - 2 * margin).coerceAtLeast(dp(90))
        val maxWByHeight = (maximumHeight.toFloat() * capturedWidth / capturedHeight).toInt()
        val maxW = min(screen.x - margin, maxWByHeight).coerceAtLeast(minW)
        val width = requestedWidth.coerceIn(minW, maxW)
        layout.width = width
        layout.height = barHeight + width * capturedHeight / capturedWidth
        clampWindowPosition()
    }

    private fun clampWindowPosition() {
        if (!::layout.isInitialized || overlay == null) return
        val size = getDisplaySize()
        layout.x = layout.x.coerceIn(0, (size.x - layout.width).coerceAtLeast(0))
        layout.y = layout.y.coerceIn(0, (size.y - layout.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(overlay, layout) }
    }

    private fun showOverlay() {
        barHeight = dp(43)
        val size = getDisplaySize()
        val initialWidth = (size.x * 0.55f).toInt().coerceAtLeast(dp(240))
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(6, 10, 15)) }
        overlay = frame
        val preview = TextureView(this).apply { isOpaque = true }
        textureView = preview
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(18, 45, 61))
        }
        val handle = TextView(this).apply {
            text = "☰ PRZESUŃ"
            textSize = 12f
            setTextColor(0xFFE5F9FF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        header.addView(handle, LinearLayout.LayoutParams(0, barHeight, 1f))
        val bubble = TextView(this).apply {
            text = "📷"
            textSize = 25f
            gravity = Gravity.CENTER
            contentDescription = "Otwórz podgląd ekranu"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(18, 45, 61))
                setStroke(dp(2), Color.rgb(123, 224, 239))
            }
            visibility = View.GONE
            // Ikona pozostaje niezależnym, przesuwalnym uchwytem po minimalizacji.
        }
        header.addView(controlButton("▁") {
            // Zapamiętujemy geometrię okna. Samo przechwytywanie i Surface pozostają aktywne.
            if (!minimized) {
                expandedX = layout.x
                expandedY = layout.y
                expandedWidth = layout.width
                expandedHeight = layout.height
                minimized = true
                panel.visibility = View.INVISIBLE
                bubble.visibility = View.VISIBLE
                frame.setBackgroundColor(Color.TRANSPARENT)
                layout.width = dp(58)
                layout.height = dp(58)
                clampWindowPosition()
            }
        })
        header.addView(controlButton("−") { updateWindowSize((layout.width * .82f).toInt()) })
        header.addView(controlButton("+") { updateWindowSize((layout.width * 1.22f).toInt()) })
        header.addView(controlButton("×") { stopSelf() })
        panel.addView(header, LinearLayout.LayoutParams(-1, barHeight))
        panel.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))
        frame.addView(bubble, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))

        // Przesuwanie ikonki. Krótkie dotknięcie przywraca okno w poprzednim miejscu i rozmiarze.
        var bubbleDownX = 0f
        var bubbleDownY = 0f
        var bubbleOriginX = 0
        var bubbleOriginY = 0
        var bubbleMoved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleDownX = event.rawX
                    bubbleDownY = event.rawY
                    bubbleOriginX = layout.x
                    bubbleOriginY = layout.y
                    bubbleMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - bubbleDownX
                    val dy = event.rawY - bubbleDownY
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        bubbleMoved = true
                    }
                    if (bubbleMoved) {
                        layout.x = bubbleOriginX + dx.toInt()
                        layout.y = bubbleOriginY + dy.toInt()
                        clampWindowPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!bubbleMoved && minimized) {
                        minimized = false
                        bubble.visibility = View.GONE
                        panel.visibility = View.VISIBLE
                        frame.setBackgroundColor(Color.rgb(6, 10, 15))
                        layout.x = expandedX
                        layout.y = expandedY
                        layout.width = expandedWidth
                        layout.height = expandedHeight
                        clampWindowPosition()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        layout = WindowManager.LayoutParams(
            initialWidth, barHeight + initialWidth * capturedHeight / capturedWidth,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                // Usuwa efekt lustra-w-lustrze: okno nie trafia do własnego nagrania.
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (size.x - initialWidth - dp(12)).coerceAtLeast(0)
            y = dp(70)
        }

        // Pasek przeciągania. Wideo może pozostać nietykalne dla gestów przesuwania.
        var startRawX = 0f
        var startRawY = 0f
        var originX = 0
        var originY = 0
        val dragListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    originX = layout.x
                    originY = layout.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layout.x = originX + (event.rawX - startRawX).toInt()
                    layout.y = originY + (event.rawY - startRawY).toInt()
                    clampWindowPosition()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        handle.setOnTouchListener(dragListener)

        // Skalowanie dwoma palcami bez tworzenia drugiej sesji MediaProjection.
        val pinch = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    updateWindowSize((layout.width * detector.scaleFactor).toInt())
                    return true
                }
            })
        preview.setOnTouchListener { _, event ->
            pinch.onTouchEvent(event)
            true
        }

        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                surfaceTexture.setDefaultBufferSize(capturedWidth, capturedHeight)
                val surface = Surface(surfaceTexture)
                previewSurface?.release()
                previewSurface = surface
                try {
                    val vd = virtualDisplay
                    when {
                        vd != null -> vd.setSurface(surface)
                        !captureStarted -> {
                            // Na Androidzie 14+ createVirtualDisplay wolno wywołać tylko JEDEN raz.
                            captureStarted = true
                            virtualDisplay = projection?.createVirtualDisplay(
                                "ScreenWindow", capturedWidth, capturedHeight,
                                screenDensityDpi,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                                surface, null, handler
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Creating virtual display failed", e)
                    Toast.makeText(this@MirrorService, "Błąd obrazu: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }
            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                // Wirtualny ekran trzyma własną rozdzielczość, TextureView skaluje podgląd.
            }
            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                runCatching { virtualDisplay?.setSurface(null) }
                previewSurface?.release()
                previewSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
        }
        windowManager.addView(frame, layout)
        updateWindowSize(initialWidth)
    }

    private fun controlButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 19f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(if (label == "×") 0xFF823649.toInt() else 0xFF245469.toInt())
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(2) }
        contentDescription = when (label) {
            "−" -> "Zmniejsz okno"
            "+" -> "Powiększ okno"
            "▁" -> "Minimalizuj do pływającej ikonki"
            else -> "Zamknij podgląd"
        }
    }

    override fun onDestroy() {
        stopping = true
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { previewSurface?.release() }
        previewSurface = null
        val activeProjection = projection
        callback?.let { cb -> runCatching { activeProjection?.unregisterCallback(cb) } }
        callback = null
        projection = null
        runCatching { activeProjection?.stop() }
        overlay?.let { box ->
            if (::windowManager.isInitialized) runCatching { windowManager.removeViewImmediate(box) }
        }
        overlay = null
        textureView = null
        super.onDestroy()
    }
}
