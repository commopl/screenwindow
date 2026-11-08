# ScreenWindow 1.5

Aplikacja Android 8+ napisana w Kotlinie: podgląd ekranu w pływającym oknie nad innymi aplikacjami.

**Repozytorium projektu:** `commopl/screenwindow` (prywatne).

## Funkcje
- Pływający podgląd ekranu z paskiem do przesuwania, przyciskami minimalizacji, +, −, × i gestem szczypania. Minimalizacja zachowuje sesję przechwytywania, a półprzezroczystą ikonę można przesuwać; dotknięcie przywraca poprzedni rozmiar i położenie.
- Jakość przechwytywania 40%, 60% lub 80%.
- Obsługa obrotu ekranu i zmiany rozmiaru przy pojedynczej sesji MediaProjection (Android 14+).
- Powiadomienie z akcją zatrzymania.

## Pobieranie APK
Po udanym buildzie pobierz `ScreenWindow-v1.5-debug.apk` z wydania [v1.5-debug](https://github.com/commopl/screenwindow/releases/tag/v1.5-debug).
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

Wydanie 1.5 korzysta z osobnego Cloudflare Worker `screenwindow-stream` i stałego identyfikatora publicznego pokoju.
- Odtwarzacz: https://screenwindow-stream.dam-mniamx.workers.dev/
- Aplikacja nadaje do jednego stałego pokoju o identyfikatorze `00000000000000000000000000000001`; odtwarzacz publiczny jest dostępny pod `/public`.
- Przycisk **Kopiuj link odtwarzacza** kopiuje stały publiczny adres: https://screenwindow-stream.dam-mniamx.workers.dev/public.
- Po uruchomieniu przechwytywania obraz jest wysyłany przez WebSocket (JPEG, około 8 kl./s) do pokoju, a odtwarzacz pokazuje bieżące klatki.
- Pokój jest publiczny: każdy, kto zna adres, może oglądać transmisję. Nie jest wymagany kod ani zaproszenie. Nie pokazuj prywatnych danych na ekranie.
- To jest przesyłanie obrazu ekranu do przeglądarki, nie systemowa wirtualna kamera Androida.

Worker i Durable Object: `cloudflare/worker.js`, konfiguracja: `cloudflare/wrangler.jsonc`.
