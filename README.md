# ScreenWindow 1.4

Aplikacja Android 8+ napisana w Kotlinie: podgląd ekranu w pływającym oknie nad innymi aplikacjami.

**Repozytorium projektu:** `commopl/screenwindow` (prywatne).

## Funkcje
- Pływający podgląd ekranu z paskiem do przesuwania, przyciskami minimalizacji, +, −, × i gestem szczypania. Minimalizacja zachowuje sesję przechwytywania, a półprzezroczystą ikonę można przesuwać; dotknięcie przywraca poprzedni rozmiar i położenie.
- Jakość przechwytywania 40%, 60% lub 80%.
- Obsługa obrotu ekranu i zmiany rozmiaru przy pojedynczej sesji MediaProjection (Android 14+).
- Powiadomienie z akcją zatrzymania.

## Pobieranie APK
Po udanym buildzie wejdź do **Releases** i pobierz `ScreenWindow-v1.3-debug.apk`.
Alternatywnie otwórz **Actions → Build ScreenWindow APK → najnowszy udany przebieg → Artifacts**.
Wydanie jest testowe: plik debug APK służy do instalacji i testowania, nie jest podpisaną wersją produkcyjną.

## Budowanie
Workflow działa automatycznie po każdym push na `main`, a także ręcznie (`workflow_dispatch`).
W repo jest projekt Gradle. Używamy Gradle 8.7 zainstalowanego przez GitHub Actions, bo archiwum nie zawiera plików wykonywalnych Gradle Wrapper.

Lokalnie: otwórz projekt w Android Studio lub użyj `gradle :app:assembleDebug` z JDK 17 i Android SDK 34.

## Wymagane zgody
Aplikacja potrzebuje zgody na rysowanie nad innymi aplikacjami oraz każdorazowej zgody na przechwytywanie ekranu (MediaProjection). Zabezpieczona treść DRM może być czarna. Nie omija zabezpieczeń Androida.

**Uwaga:** sukces kompilacji nie zastępuje testów działania na fizycznym telefonie.


## Transmisja do odtwarzacza WWW

Wydanie z transmisją używa osobnego Cloudflare Worker `screenwindow-stream`:
- Odtwarzacz: https://screenwindow-stream.dam-mniamx.workers.dev/
- Aplikacja generuje losowy, 128-bitowy kod pokoju i link do odtwarzacza.
- Przycisk **Kopiuj link odtwarzacza** kopiuje adres do schowka.
- Po uruchomieniu przechwytywania obraz jest wysyłany przez WebSocket (JPEG, około 8 kl./s) do pokoju, a odtwarzacz pokazuje bieżące klatki.
- Kod pokoju jest sekretem dostępu. Nie publikuj go publicznie, jeśli transmisja ma być prywatna.
- To jest przesyłanie obrazu ekranu do przeglądarki, nie systemowa wirtualna kamera Androida.

Worker i Durable Object: `cloudflare/worker.js`, konfiguracja: `cloudflare/wrangler.jsonc`.
