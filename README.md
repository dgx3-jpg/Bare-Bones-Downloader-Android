# Bare Bones Downloader — Android

Native Kotlin / Jetpack Compose / Material 3 Android app. **Milestone 1 is a UI scaffold:** every queue/history item is fake, progress is static, and no media is downloaded, processed, opened, or shared. Settings are real and persist locally.

## Build

Open this repository root in Android Studio, or install JDK 17 or 21 and Android SDK Platform 36 / Build Tools 35.0.0. Set `ANDROID_HOME` to the SDK, or put `sdk.dir=/your/sdk/path` in an untracked `local.properties`.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleDebugAndroidTest
# Requires a connected API 26+ phone/emulator:
./gradlew connectedDebugAndroidTest
```

Windows: use `gradlew.bat` instead of `./gradlew`. The wrapper pins Gradle 8.13 and verifies its distribution SHA-256. Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.01 and all other direct dependencies are pinned in Gradle files. Compile/target SDK 36 is supported by this toolchain; minSdk is 26. No extractor or native FFmpeg dependency is bundled in this milestone.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Debug signing is automatic. Release signing is not configured; a signed release APK and full release instructions belong to milestone 6. Never commit signing keys.

In a proxy-managed cloud environment, set `GRADLE_OPTS` with your Java HTTP/HTTPS proxy settings; the wrapper bootstrap requires those settings too. Keep local Gradle caches, Android user state, and SDK/JDK installations outside Git.

## What to try

- Navigate Home, Queue, History, and Settings using the bottom bar.
- Paste a URL explicitly, or type one; malformed/non-HTTP URLs show a validation message.
- Choose a save folder with Android's folder picker before adding a valid URL. The app retains URI permission and stores the selection, but does not write files yet. Tap Add to queue again after choosing.
- Change format/quality, metadata fields or ReplayGain and restart the app. These choices persist through DataStore Preferences. FLAC/WAV show Source-limited quality.
- Open Preview tags: the Come Together example is static and labelled illustrative. Disabled fields disappear; absent values remain empty.
- Demo queue actions change in-memory state only. Demo Open/Share and extractor updates are disabled. Queue/history reset when the process restarts. Clear history requires confirmation and never touches media.

## Structure

- `ui/`: navigation, four screens, shared cards, theme, and ViewModel.
- `domain/model/`: settings defaults, immutable enqueue snapshots, statuses, Room entity.
- `data/settings/`: atomic DataStore settings repository.
- `data/repository/`: download repository contract and explicit fake implementation.
- `data/db/`: Room database, DAO, converters and exported v1 schema; not connected to demo UI.
- `src/test/`: model, on-disk settings persistence, Room and Robolectric Compose UI tests.
- `src/androidTest/`: device navigation smoke test.

The Room-backed coordinator, engine/services, processing, storage writes and Android share target are intentionally deferred to their specified milestones. See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for actual verification and [ANDROID_DOWNLOADER_SPEC.md](ANDROID_DOWNLOADER_SPEC.md) for scope. The uploaded document is recorded as the product specification; task scope remains milestone 1 only.
