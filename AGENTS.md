# JAViewer

Android app for browsing JAV movies. Kotlin + Jetpack Compose + Material 3.

## Build & Run

```bash
# Full build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

- **compileSdk 35 / minSdk 21 / targetSdk 35**
- **Java 17 required** (`jvmTarget = '17'`)
- **Gradle JVM**: 2GB heap (`-Xmx2048m`), parallel + caching enabled
- No CI pipelines. No lint or formatting configs beyond Kotlin defaults (`kotlin.code.style=official`).

## Architecture

Single-module Gradle project (`app/`). Single-Activity with Compose Navigation.

### Entry Points

- `activity/StartActivity.kt` — first screen; loads config, fetches remote properties, then launches MainActivity
- `activity/MainActivity.kt` — sets up Compose content with `MainScreen`
- `ui/screen/MainScreen.kt` — drawer navigation + top bar, hosts `JAViewerNavHost`
- `ui/navigation/` — `NavRoutes.kt` (route constants) + `JAViewerNavHost.kt` (composable definitions)

### Data Flow

`JAViewerApp.kt` (`JAViewer` Application class) holds singletons:
- `CONFIGURATIONS` — loaded from `configurations.json` on external storage
- `SERVICE` / `AVMOO_API_SERVICE` — Retrofit services, recreated when data source changes
- `HTTP_CLIENT` — shared OkHttp with UA spoofing + cookie jar + host replacement
- `DATA_SOURCES` — fetched from remote `properties.json` at startup

### Package Layout

```
io.github.javcinema/
├── JAViewerApp.kt          # Application + global singletons
├── activity/                # StartActivity + MainActivity (Compose)
├── data/model/              # Data classes (Movie, Actress, Genre, etc.)
├── network/                 # Retrofit interfaces + Jsoup parsers
├── player/                  # Media3 ExoPlayer wrapper + Compose player UI
├── ui/components/           # Reusable composables
├── ui/navigation/           # Compose Navigation routes + host
├── ui/screen/               # Compose screens + ViewModels
├── ui/theme/                # Material 3 theme (Color, Type, Theme)
└── util/                    # IOUtils
```

## Migration Status

**Migration from Java/XML to Kotlin/Compose is complete.**

| Layer | Status |
|-------|--------|
| Language | 100% Kotlin (0 Java files) |
| UI | 100% Compose + Material 3 |
| Navigation | 100% Compose Navigation |
| Image loading | 100% Coil 2.7 |
| Player | 100% Media3 ExoPlayer 1.5 |
| Async | 100% Coroutines + Flow |

**When adding features:**
- Write new screens in Compose under `ui/screen/`
- Use ViewModels with `lifecycle-viewmodel-compose`
- Use `kotlinx.coroutines` + `Flow` for async — no RxJava
- Use Coil for images in Compose code

## Key Quirks

- **`com.intellij.annotations` is globally excluded** — `configurations.all { exclude group: 'com.intellij', module: 'annotations' }`
- **Data sources are dynamic** — fetched from GitHub at startup; base URLs change. Code uses `hostReplacements` map to rewrite legacy hosts.
- **`Configurations` uses Gson file I/O** — not DataStore or SharedPreferences (except for custom URLs which use SharedPreferences)
- **Retrofit services are suspend functions** — use coroutines, not callbacks
- **Jsoup is used for HTML parsing** of scraped pages (not for API responses)
- **Media3 `ExoPlayerImpl` wraps `ExoPlayer` directly** — no DI; instantiated in `PlayerScreen` composable via `remember`

## Dependencies

- **Compose BOM 2024.12.01** — pins all Compose versions
- **Retrofit 2.11 + OkHttp 4.12** — networking
- **Jsoup 1.18** — HTML scraping for download links, actress data
- **Coil 2.7** — image loading in Compose
- **Media3 1.5** — video playback (ExoPlayer successor)
- **Material 1.12** — XML themes for Activity manifest entries

## Conventions

- Language: Kotlin only. No new Java.
- UI: Compose + Material 3. No XML layouts.
- Async: Coroutines + Flow. No RxJava.
- State: ViewModels with `collectAsState`. No LiveData.
- JSON: Gson (used throughout)
- Strings: Chinese for UI labels (user-facing app is Chinese-language)
