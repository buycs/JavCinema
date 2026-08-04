# JavCinema

Android app for browsing JAV movies. Kotlin + Jetpack Compose + Material 3. 100% Kotlin, 0 Java files.

## Build & Run

```bash
# Full build
./gradlew.bat assembleDebug

# Install on connected device
./gradlew.bat installDebug

# Release build (signed with debug key + R8)
./gradlew.bat assembleRelease
```

- **compileSdk 35 / minSdk 21 / targetSdk 35**
- **Java 17 required** (`jvmTarget = '17'`)
- **Gradle JVM**: 2GB heap (`-Xmx2048m`), parallel + caching enabled
- **Must use `./gradlew.bat`** on this machine — a global `init.gradle` injects a conflicting Aliyun Maven mirror into plain `gradle` invocations.
- No CI pipelines. No lint or formatting configs beyond Kotlin defaults (`kotlin.code.style=official`).

## Architecture

Single-module Gradle project (`app/`). Single-Activity with Compose Navigation.

### Entry Points

- `activity/StartActivity.kt` — first screen; loads local `properties.json`, fetches remote properties, launches MainActivity
- `activity/MainActivity.kt` — sets up Compose content with `MainScreen`
- `ui/screen/MainScreen.kt` — **bottom navigation bar** + top bar, hosts `JavCinemaNavHost`
- `ui/navigation/` — `NavRoutes.kt` (12 route constants) + `JavCinemaNavHost.kt` (composable definitions)

### Data Flow

`JavCinemaApp.kt` (`JavCinema` Application class) holds singletons:
- `CONFIGURATIONS` — loaded from `configurations.json` on external storage; also Gson-file cached
- `SERVICE` (BasicService, HTML scraping) / `AVMOO_API_SERVICE` (AvmooApiService, JSON API) — Retrofit services, recreated when data source changes
- `HTTP_CLIENT` — shared OkHttp with UA spoofing + cookie jar + host replacement
- `DATA_SOURCES` — fetched from remote `properties.json` at startup (three sources: 骑兵/步兵/欧美)
- `imageUrlsRegistry` — poster URL registry (thumb `ps.jpg` ↔ large `pl.jpg`) shared by all image loading
- `prefetchedLargeCovers` + `prefetchSemaphore` — cover prefetch pool; `screenshotImageLoader`/`coverImageLoader`/`main ImageLoader` pools

### Package Layout

```
io.github.javcinema/
├── JavCinemaApp.kt          # Application + global singletons + Coil/OkHttp/Retrofit
├── activity/                # StartActivity + MainActivity (Compose)
├── data/model/              # Gson data classes (Movie, MovieDetail, Actress, Genre, Screenshot, DownloadLink, MagnetLink, MagnetFile, TorrentGroup, Properties, Configurations, DataSource, Linkable...)
├── network/                 # Retrofit services + parsers
│   ├── AvmooApiService.kt   #   JSON API (getMovies/getMovie/getRelatedMovies/getFilterMovies/getStars/getStar/getGenres)
│   ├── BasicService.kt      #   HTML scraping fallback (getHomePage/getReleased/getPopular/getActresses/getGenre/get)
│   ├── BtSearch.kt          #   btsearch.love JSON API (MD5 signing: x-timestamp/x-nonce/x-sign)
│   ├── BTSO.kt              #   btsow.live JSON API (POST /bts/data/api/search, /bts/data/api/magnet)
│   ├── CiliInfo.kt          #   cili.info HTML scraping
│   ├── PSVS.kt / Avgle.kt   #   other torrent/stream sources
│   ├── TorrentKitty.kt      #   torrentkitty JSON API
│   └── RetryInterceptor.kt  #   HTTP retry interceptor
│   └── provider/            #   AVMOProvider + link providers (BTSOLinkProvider, BtSearchLinkProvider, CiliInfoLinkProvider, TorrentKittyLinkProvider) extending abstract DownloadLinkProvider
├── player/                  # Media3 ExoPlayer wrapper + Compose player UI (ExoPlayerImpl, PlayerScreen, SimpleVideoPlayer)
├── ui/components/           # Reusable composables (MovieCard, MovieFavoriteDialog, SwipeBackContainer, BottomNavigationBar, ...)
├── ui/navigation/           # Compose Navigation routes + host
├── ui/screen/               # Compose screens + ViewModels (StateFlow)
├── ui/theme/                # Material 3 theme (Color, Type, Theme)
└── util/                    # IOUtils (UTF-8 read/write, saveImageToGallery)
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
| ViewModel state | StateFlow + `collectAsState` |

**When adding features:**
- Write new screens in Compose under `ui/screen/`
- Use ViewModels with `lifecycle-viewmodel-compose`
- Use `kotlinx.coroutines` + `Flow`/StateFlow for async — no RxJava
- Use Coil for images in Compose code

## Release Build / R8

Release is configured in `app/build.gradle`:
- `minifyEnabled true` + `proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'`
- `signingConfig signingConfigs.debug` — release APKs are signed with the debug key (for now)
- `ndk.abiFilters` + ABI splits → named split APKs
- `app/proguard-rules.pro` contains keep rules for:
  - Gson reflection (`@Keep` or keeps on model classes, `keepattributes Signature`, `-keepclassmembers`)
  - Retrofit services (`-keep,allowobfuscation interface io.github.javcinema.network.*` etc.)
  - Coil, OkHttp internals, Jsoup

**When adding data classes used by Gson or Retrofit interfaces:** add keep rules to `app/proguard-rules.pro` or R8 will strip/rename them and release builds crash.

## Key Quirks

- **`com.intellij.annotations` is globally excluded** — `configurations.all { exclude group: 'com.intellij', module: 'annotations' }`
- **Data sources are dynamic** — fetched from GitHub at startup; base URLs change. Code uses `hostReplacements` map to rewrite legacy hosts. Domain list lives in `app/src/main/assets/properties.json`.
- **`Configurations` uses Gson file I/O** — not DataStore or SharedPreferences (except for custom URLs which use SharedPreferences)
- **Two parallel API paths for AVM:**
  - `AvmooApiService` — modern JSON API (`POST` with `["home", 60, page]`-style body arrays; `getMovies/getMovie/getRelatedMovies/getFilterMovies/getStars/getStar/getGenres`)
  - `BasicService` — legacy HTML scraping (`GET` `/cn/page/{page}` etc.) with Jsoup parsing
  - `AVMOProvider` in `network/provider/` decides which to use per operation and parses models from either source
- **Retrofit services are suspend functions** — use coroutines, not callbacks
- **Jsoup is used for HTML parsing** of scraped pages (not for API responses)
- **Media3 `ExoPlayerImpl` wraps `ExoPlayer` directly** — no DI; instantiated in `PlayerScreen` composable via `remember`
- **Network security** allows cleartext + user CAs (`res/xml/network_security_config.xml`) because AVM domains are plain HTTP at times

## Dependencies

- **Compose BOM 2024.12.01** — pins all Compose versions
- **Retrofit 2.11 + OkHttp 4.12** — networking
- **Gson** — JSON parsing
- **Jsoup 1.18** — HTML scraping for download links, actress data
- **Coil 2.7** — image loading in Compose
- **Media3 1.5** — video playback (ExoPlayer successor)
- **Material 1.12** — XML themes for Activity manifest entries
- **lifecycle-viewmodel-compose** — ViewModel in Compose

## Image Loading

Coil is configured in `JavCinemaApp.kt`:
- Main `ImageLoader` (singleton via `Coil.setImageLoader`) — shared OkHttp client with connection pool.
- `screenshotImageLoader` — separate `ImageLoader` with `OkHttpClient.newBuilder().dispatcher(maxRequestsPerHost=2)` to avoid screenshot requests blocking the main pool.
- `coverImageLoader` — dedicated pool for cover/large poster loading (parallel loaders).
- Components (`ScreenshotRow`, `ActressRow`) accept an optional `imageLoader` parameter for fine-grained control.
- `imageUrlsRegistry` maps a movie link → `ImageUrls(posterLarge, posterThumb, screenshot...)`; `MovieCard` prefetch logic and `MovieDetail` use it to swap `ps.jpg` ↔ `pl.jpg`.
- Screenshot loading in detail is split from cover loading (deduplicated via `loadDetail` guard to prevent double API requests on NavHost recomposition).

### Detail Page Loading Flow

1. **Pre-navigation**: thumbnail URL passed via nav args; `remember` block enqueues the thumbnail into Coil's cache before the screen even mounts.
2. **Loading state**: blurred thumbnail (`rememberAsyncImagePainter`) with `animateFloatAsState(20→0, tween(600))` + `CircularProgressIndicator` overlay.
3. **API returns** → `Success` state: `LaunchedEffect` immediately enqueues the large cover URL into the main `ImageLoader` (preloads before `MovieDetailContent`'s `AsyncImage` starts).
4. **MovieDetailContent**: renders full content with `AsyncImage` for the large cover (reads from cache when available).

## Conventions

- Language: Kotlin only. No new Java.
- UI: Compose + Material 3. No XML layouts.
- Async: Coroutines + Flow. No RxJava.
- State: ViewModels with `collectAsState`. No LiveData.
- JSON: Gson (used throughout)
- Strings: Chinese for UI labels (user-facing app is Chinese-language)
