# Implementation status

Requested scope: milestones 2 and 3 of design v2. Both are implemented. The owner explicitly instructed “Continue” after being informed that M2's live YouTube acceptance gate was blocked by site authentication. This authorizes M3 implementation with the verification limit recorded; no successful live download or full parity is claimed.

## Milestone 2 implementation

- Strict canonical YouTube watch/Shorts/youtu.be parsing, single-item/live/collection validation, per-source+kind duplicate reservations and complete enqueue snapshots.
- Room-backed FIFO/history and monotonic order; one foreground worker; notification/cancel; bounded partial wake lock; interrupted recovery, explicit Resume/Retry, failure continuation and reservation release.
- Separate SAF folders, per-type first selection, revoked-folder pause, explicit replacement of matching waiting jobs only.
- Pinned youtubedl-android 0.18.1 Python/QuickJS/yt-dlp, FFmpeg and ffprobe. Structured subprocess arguments, per-command session/handshake, group cancellation, bounded output/deadlines, coroutine cancellation and output draining.
- Best-source audio with stream-copy demux where needed; compatible real H.264/AAC MP4 selection and ffprobe checks; private staging; Unicode-safe title names/optional URL suffix; verified SAF copy; collisions skipped; incomplete destination deletion on failure.
- Source errors surface clearly. Authentication is explicitly unsupported; no cookie import, client impersonation or bypass is added.
- Type-specific size caps are blocked until M4. Metadata/ReplayGain remain M5 warnings; existing tags may remain. No fake repository is wired into the production app.

## Actual local verification

OpenJDK 21.0.13, SDK 36, Build Tools 35.0.0, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21.

```sh
JAVA_HOME=/workspace/work/jdk21 \
ANDROID_USER_HOME=/workspace/work/android-user \
GRADLE_USER_HOME=/workspace/work/gradle-home \
GRADLE_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080' \
./gradlew --init-script /workspace/work/proxy.init.gradle \
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

M2 local command passed all 20 JVM/Robolectric tests. M3 adds encoding-mode, encoder-limit, locale and every-selector-fallback tests; final verification is recorded below. Coverage: settings/defaults/persistence, strict parsing and spoof/collection/live rejection, names/Unicode, format preference availability, input/mode/draft behavior, Room snapshots and duplicate reservations, FIFO/failure/folder recovery, non-destructive schema 1→2 history migration, and safe error classification. Scheduling tests use explicit fakes, not native output. Lint passed with no errors; warnings remain. App and instrumentation APKs compiled. Reports are under `app/build/reports/` and `app/build/test-results/`.

## Actual Android emulator / live evidence

GitHub-hosted Android 10 / API 29, x86_64:

- [Run 42480384765](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42480384765): real native FFmpeg/ffprobe **7.1.1**, bundled yt-dlp **2025.11.12** executed; owned H.264/AAC fixture validated; process-group descendant cancellation passed. Initial live test stopped at an Android-10 DocumentsUI label mismatch, corrected in test code.
- [Run 42481332660](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42481332660): CI build/JVM tests passed. Real DocumentsUI grant succeeded; the old yt-dlp fixture `BaW_jenozKc` returned “This video is unavailable”. No live saving success.
- [Run 42482139097](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42482139097): CI build/JVM tests passed; owned native media/cancellation passed again. The attributed CC Blender fixture `aqz-KE-bpKQ` failed with **“Sign in to confirm you're not a bot”**. Both requested jobs failed instead of falsely completing. No authentication bypass was attempted.
- [Run 42483553996](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42483553996): build and independent Android native suite passed, including real SAF audio/video byte-for-byte read-back, collision protection and cancelled-copy cleanup. The opt-in live test was skipped, not counted as passing.

No physical phone testing was performed. Local emulator installation/startup was attempted; absent KVM and the workspace disk budget prevented startup, so unused images were removed and GitHub-hosted KVM was used. Other ABIs, 16 KB page-size devices, screen-lock/OEM behavior and full recovery/publication hardening are not verified.

## Milestone 3 implementation

- Nine formats, actual codec/container/extension validation, 16-bit PCM WAV, compatible default copy and explicit re-encoding.
- Supported variable quality 0–10 and bitrate modes; codec-specific limits fail explicitly. MP3 quality 10 maps to LAME 9; AAC quality mapping is locale independent; Vorbis reverses its encoder scale.
- All seven video height ceilings apply to both selector alternatives; output checks reject exceeding height, wrong codecs/container and cover-art-only video.
- Unicode/UTF-8 bounded names, reserved names including `CON.txt`, audio title-only and video source-ID names, optional URL suffix and collision protection.
- Official yt-dlp 2026.10.26 is build-time downloaded, SHA-256 verified and pinned into the APK; a hash-checked replacement refreshes stale app-private unpacked extractors. No in-app updater.
- Android tests generate owned PCM audio, encode/decode all nine formats and every supported mode, verify compatible copy versus explicit re-encode, and enforce a real video height ceiling. Execution results follow when CI completes.

M2's design says “Test real audio AND video before proceeding.” Real owned audio/video native processing and SAF publication passed; the intended live YouTube extraction/save gate remains blocked in the older extractor's CI run. The owner authorized proceeding with M3. No login/cookies/evasion is added; current-extractor live verification remains separate.

Current app: **0.3.0 / code 5**, stable package `com.barebonesdownloader.android`. Debug identity uses the existing cached key; cache eviction remains a signing-continuity limitation. [Actions APKs/reports](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml), [native provenance/licenses](docs/NATIVE_RUNTIME.md), [parity map](docs/DESKTOP_PARITY.md). Native GPL distribution obligations apply; this remains the owner's private personal-use build.
