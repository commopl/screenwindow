# ScreenWindow 1.6

Aplikacja Android 8+ napisana w Kotlinie: podgląd ekranu w pływającym oknie nad innymi aplikacjami.

**Repozytorium projektu:** `commopl/screenwindow` (prywatne).

## Funkcje
- Pływający podgląd ekranu z paskiem do przesuwania, przyciskami minimalizacji, +, −, × i gestem szczypania. Minimalizacja zachowuje sesję przechwytywania, a półprzezroczystą ikonę można przesuwać; dotknięcie przywraca poprzedni rozmiar i położenie.
- Jakość przechwytywania 40%, 60% lub 80%.
- Obsługa obrotu ekranu i zmiany rozmiaru przy pojedynczej sesji MediaProjection (Android 14+).
- Powiadomienie z akcją zatrzymania.

## Pobieranie APK
Po udanym buildzie pobierz `ScreenWindow-v1.6-debug.apk` z wydania [v1.6-debug](https://github.com/commopl/screenwindow/releases/tag/v1.6-debug).
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

Wydanie 1.6 korzysta z osobnego Cloudflare Worker `screenwindow-stream` i stałego identyfikatora publicznego pokoju.
- Odtwarzacz: https://screenwindow-stream.dam-mniamx.workers.dev/
- Aplikacja nadaje do jednego stałego pokoju o identyfikatorze `00000000000000000000000000000001`; odtwarzacz publiczny jest dostępny pod `/public`.
- Przycisk **Kopiuj link odtwarzacza** kopiuje stały publiczny adres: https://screenwindow-stream.dam-mniamx.workers.dev/public.
- Po uruchomieniu przechwytywania obraz jest wysyłany przez WebSocket (JPEG, około 8 kl./s) do pokoju, a odtwarzacz pokazuje bieżące klatki.
- Pokój jest publiczny: każdy, kto zna adres, może oglądać transmisję. Nie jest wymagany kod ani zaproszenie. Nie pokazuj prywatnych danych na ekranie.
- To jest przesyłanie obrazu ekranu do przeglądarki, nie systemowa wirtualna kamera Androida.

Worker i Durable Object: `cloudflare/worker.js`, konfiguracja: `cloudflare/wrangler.jsonc`.

## Link do aktualnej klatki dla Miry

W wersji 1.6 aplikacja tworzy losowy, zapisany na urządzeniu 32-znakowy kod pokoju. Po uruchomieniu przechwytywania przycisk **Kopiuj link dla Miry (klatka)** kopiuje adres `https://screenwindow-stream.dam-mniamx.workers.dev/snapshot/<kod>.jpg`. Endpoint oddaje ostatnią klatkę JPEG z bieżącej transmisji (maksymalnie 10 sekund starą), a nie zapis archiwalny. Funkcja wymaga wdrożenia aktualnego `cloudflare/worker.js` na Cloudflare.

**Prywatność:** adres jest sekretnym linkiem, bez dodatkowego uwierzytelniania. Każda osoba znająca go może zobaczyć aktualny ekran podczas transmisji. Linku nie publikuj i zatrzymaj nadawanie przed pokazywaniem poufnych danych. ChatGPT nie otrzymuje obrazu automatycznie: może odczytać klatkę jedynie podczas aktywnego pobrania adresu, jeśli narzędzia ją udostępnią.
