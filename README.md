# Bare Bones Downloader for Android

Standalone native Android shell following [design v2](ANDROID_DOWNLOADER_SPEC.md), using the [Windows app](https://github.com/dgx3-jpg/BareBonesDownloader) as its behavioral/visual reference. **Milestone 1 only:** queue/progress/log activity is illustrative; no extraction, audio/video processing or file publication is implemented.

## Build and install

Open the repository root in Android Studio, or install JDK 17/21, Android SDK Platform 36 and Build Tools 35.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir=/your/sdk/path`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
# With an Android 10+ device/emulator connected:
./gradlew connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use `gradlew.bat` on Windows. Gradle 8.13 is checksum verified; AGP 8.13.2, Kotlin 2.2.21 and dependency versions are pinned. SDK 36 compile/target, minSdk 29. Robolectric tests run simulated API 35; they do not establish device integration.

**GitHub APK:** open [Actions → Android debug APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml), choose a successful run, then download **app-debug-apk** under Artifacts and unzip `app-debug.apk`. Signing keys are never included in the artifact. CI runs on pushes/PRs and can be run manually. Build/test reports are a separate artifact.

## Shell behavior

- Main screen: fresh-launch Audio only, Video + audio switch, type-specific destination, multiline links with explicit Paste, Queue audio/Queue video, Cancel current, Clear waiting, compact URL/Type/Status cards and a collapsible/copyable log capped at 60,000 characters.
- Mode changes preserve input and queued placeholders. Folder selection is requested only for the relevant type. Cancelling keeps input. The shell does basic HTTP(S) validation and retains rejected tokens; real YouTube normalization, duplicate reservations and extraction are milestone 2.
- Settings gear opens General / Audio / Video / Metadata / Appearance. Changes remain draft until Save; Cancel/back discards them. Two SAF destinations can be the same. Changes to folder selections are checked for persisted write/create permission on Save, without writing media.
- Nine format choices, supported encoding controls, independent decimal-MB caps and resolution settings are saved **preferences**, not working encoders. Unsupported quality modes are disabled. Defaults match the desktop: best/default/quality 5/192 kbps, caps off, all 11 metadata fields and ReplayGain on, Light theme.
- Metadata preview is the static Here Comes the Sun / Beatles / Abbey Road example. Deselection clears values; AAC/ALAC/M4A source URL and WAV composer/source URL/album artist show Unavailable. No network lookup or artwork is implied.
- Light/Dark is explicit and persisted. Penrose adaptive icon is converted from the original SVG. Room schema includes type, destination and complete settings snapshots; demo UI remains in-memory. No worker/background service or persistent real queue is claimed.

## Identity and signing

The v2-required ID is **com.barebonesdownloader.android**, display name Bare Bones Downloader, version 0.1.1 (code 2). This replaces the previous audio-only scaffold's `com.barebones.downloader`; Android treats it as a separate app, so it cannot inherit that draft's data through an in-place update. This v2 identity is the baseline for future milestones; retain it and migrate Room schemas from here.

Local debug builds reuse your SDK's debug keystore; keep it backed up and keep the same key for updates. CI caches its generated debug keystore under `bare-bones-android-debug-signing-v1`. **Caches can expire or be evicted**, so CI signing continuity is best effort until durable signing is configured in milestone 6. CI and local builds may use different debug keys: Android rejects in-place updates between them. Keep the same build source/key, or reinstall (which clears app data). Never commit keys or passwords.

No release signing is configured. Signed release readiness, real formats, metadata, ReplayGain and device acceptance gates remain in their specified milestones.

## Code and evidence

Single app module, MVVM/Flow; `ui/`, `domain/model/`, `data/{db,settings,repository}/`, and `storage/`. DataStore persists preferences and normalizes invalid values; Room's schema is tested separately from placeholder UI. The old four-bottom-tab layout and audio-only defaults have been replaced.

See [implementation status](IMPLEMENTATION_STATUS.md), [desktop parity map](docs/DESKTOP_PARITY.md), and [third-party notices](THIRD_PARTY_NOTICES.md). The uploaded document is the product specification; this task implements changes affecting milestone 1 only.
