# Bare Bones Downloader for Android

Native Kotlin / Compose Android app following [design v2](ANDROID_DOWNLOADER_SPEC.md), with the [desktop app](https://github.com/dgx3-jpg/BareBonesDownloader) as its behavior/visual reference. Package ID `com.barebonesdownloader.android`, Android 10+ (API 29), current milestones-2-and-3 candidate **0.3.0 / code 5**.

**Verification limit:** the live YouTube acceptance gate is blocked in CI by “Sign in to confirm you're not a bot.” No successful YouTube download/save is claimed. The app does not implement login, cookie imports or evasion. See [implementation status](IMPLEMENTATION_STATUS.md) for exact passing tests and blocked checks. The owner authorized continuing milestone 3 with this live-verification limit recorded.

## Build / APK

Use JDK 17 or 21, SDK Platform 36 and Build Tools 35.0.0. Set `ANDROID_HOME`, or an untracked `local.properties` with `sdk.dir=/your/sdk/path`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows: `gradlew.bat`. Gradle 8.13 is checksum-verified; AGP 8.13.2, Kotlin 2.2.21 and native dependencies are pinned. The universal debug APK includes four ABIs and is about 226 MB. No Windows tools or FFmpegKit is used.

[Actions → Android debug APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml) publishes **app-debug-apk** and verification reports after successful builds. Download the artifact ZIP and extract `app-debug.apk`. The optional manual workflow runs real Android native tests; its **live** input is separate from the independent fixture checks. Live tests use an attributed Creative Commons Blender clip and are intentionally opt-in.

## Current implementation

- Native utility main screen: Audio/Video switch, relevant folder, multiline links, explicit Paste, queue controls, URL/Type/Status cards, bounded/copyable diagnostics; General/Audio/Video/Metadata/Appearance draft settings with Save/Cancel.
- Strict YouTube watch/Shorts/youtu.be parsing; canonical IDs; playlist context removed; malformed URLs, channel/playlist-only/live paths rejected. Metadata inspection rejects live/collection results. Other sources are M6.
- Room-backed FIFO and history, one worker, source+kind duplicate reservations, immutable per-job settings. Explicit Resume/Retry after restart; interrupted work is never marked completed. A failed job releases its reservation and does not stop later jobs.
- User-initiated data-sync foreground service with progress/cancel notification, process-group cancellation, bounded wake lock and private staging. Notification denial is handled. Android/OEM background limits still apply.
- Separate persisted SAF folders; only the relevant type is required. Revoked/unavailable destination pauses FIFO. Explicit replacement repairs matching waiting jobs of that type. No all-files permission.
- Python/QuickJS and FFmpeg/ffprobe from `youtubedl-android` 0.18.1, with checksum-pinned official yt-dlp 2026.10.26. Stream-copy best-source audio and compatible H.264/AAC MP4 pipeline; output validated before SAF publication, copied bytes verified, incomplete destination removed on failure. Existing filenames are skipped, never intentionally overwritten. SAF providers do not universally provide atomic publication.
- Unicode-safe, bounded title filenames; audio omits source ID, video includes it; optional canonical URL suffix.

All nine audio formats are implemented: Best available, AAC/M4A, ALAC, FLAC, M4A/AAC, MP3, Opus, Vorbis and PCM16 WAV. Default preserves compatible streams; explicit variable-quality/bitrate modes encode with the selected codec. Unsupported encoder/mode/bitrate combinations fail clearly. MP3 caps at 320 kbps; variable quality 10 maps to LAME 9. Seven video resolution ceilings constrain both separate-stream and combined fallbacks; actual output height/codecs/container are validated. Size caps are explicitly blocked until M4. Metadata selection and ReplayGain processing are M5; audio completes with a visible warning that source tags may remain. Video skips those audio-only operations. No actual conversion/tag/ReplayGain success is implied by a preference control.

## Native testing

With an API 29+ emulator/device connected:

```sh
# Native owned AV/audio-format fixtures, process-group cancellation, and real SAF checks:
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.barebonesdownloader.android.NativeIntegrationTest
# Optional live extraction + saving (site availability/authentication restrictions may block it):
bash tools/run-native-tests.sh true
```

Native tests use real DocumentsUI grants and owned generated media; they do not manufacture persisted permission rows or fake native command output. Host/JVM repository tests use labelled fakes for scheduling only. No physical phone verification has been performed.

## Identity, signing and licensing

The v2 package ID is stable from 0.1.1 onward. Earlier audio-only `com.barebones.downloader` was a different app and cannot transfer data through an in-place update. Room schema 1→2 migration is tested; DataStore preferences are retained.

Local builds reuse the SDK debug keystore; back it up. Actions caches its debug key, but caches can expire/evict. CI and local keys can differ, preventing in-place updates between those build sources. Durable release/update signing remains M6. Never commit keys or secrets.

Native wrapper/FFmpeg integration uses GPL-3.0 components. This is a private personal-use build for the repository owner. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [native provenance, source/build references and redistribution obligations](docs/NATIVE_RUNTIME.md). No new license grant over the owner's original code is inferred. Licenses are available in Settings → General → Open-source notices.

[Desktop parity map](docs/DESKTOP_PARITY.md) tracks implemented code separately from actual runtime evidence. No full parity claim is made.
