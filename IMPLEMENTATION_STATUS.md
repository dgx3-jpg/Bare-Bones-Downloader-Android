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

M2 local command passed all 20 JVM/Robolectric tests. M3 local and CI verification passed **23 JVM/Robolectric tests**, lint (zero errors), debug APK and instrumentation compilation. Added coverage includes encoding modes, encoder limits, locale and every selector fallback. Coverage: settings/defaults/persistence, strict parsing and spoof/collection/live rejection, names/Unicode, format preference availability, input/mode/draft behavior, Room snapshots and duplicate reservations, FIFO/failure/folder recovery, non-destructive schema 1→2 history migration, and safe error classification. Scheduling tests use explicit fakes, not native output. Lint passed with no errors; warnings remain. App and instrumentation APKs compiled. Reports are under `app/build/reports/` and `app/build/test-results/`.

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
- Android tests generate owned PCM audio, encode/decode all nine formats and every supported mode, verify compatible copy versus explicit re-encode, and enforce a real video height ceiling. All these checks passed on Android 10/x86_64 in the run below.

M2's design says “Test real audio AND video before proceeding.” Real owned audio/video native processing and SAF publication passed; the intended live YouTube extraction/save gate remains blocked with the updated extractor too. The owner authorized proceeding with M3. No login/cookies/evasion is added; current-extractor live verification failed honestly and remains an acceptance limit.

Current app: **0.3.0 / code 5**, stable package `com.barebonesdownloader.android`. Debug identity uses the existing cached key; cache eviction remains a signing-continuity limitation. [Actions APKs/reports](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml), [native provenance/licenses](docs/NATIVE_RUNTIME.md), [parity map](docs/DESKTOP_PARITY.md). Native GPL distribution obligations apply; this remains the owner's private personal-use build.

## Final M3 evidence / APK

[Run 42501942123](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42501942123), source `1f0d04c0b0611969a8d9d8d4f7c9ae5de2e0ef08`:

- Build job passed; HTML report confirms 23 tests, 0 failures, 0 ignored; lint and APK build passed.
- Android instrumented report: **5 tests, 4 passed, 1 failed, 0 skipped**. Actual yt-dlp **2026.10.26** and FFmpeg/ffprobe **7.1.1** executed.
- Passing: native H.264/AAC fixture, descendant cancellation, real SAF audio/video read-back/collision/cancel cleanup, all nine audio formats and every supported encoding mode. The format matrix decoded 22 outputs (nine defaults, eight variable-quality endpoints, five bitrate choices); compatible AAC copy and explicit AAC re-encode assertions also passed. Real video height validation passed.
- The only failure is the opt-in live YouTube case: both audio and video jobs received “Sign in to confirm you're not a bot”, surfaced as `AUTH_REQUIRED`. No file was reported saved. The overall workflow is therefore marked failed despite the successful build and independent native checks; it is not an all-green live acceptance run.
- [Download APK artifact](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42501942123/artifacts/13625932512): extract `app-debug.apk` from the ZIP. Version **0.3.0 / code 5**; artifact ZIP SHA-256 `7059ff0481535d5b1b35a93d69a56613bb77d7c92a655a7c3140dc9853a5c12c`. [Native reports](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42501942123/artifacts/13626286355).

Milestones 4–6 have not been implemented.

## Milestone 4 candidate — 0.4.0 / code 6

Independent strict decimal-MB caps are implemented. Under-cap files pass through unchanged; oversized audio uses the selected lossy codec or AAC-in-M4A for Best/lossless, and video remains H.264/AAC. Probed-duration budgets reduce conservatively over at most seven attempts. Impossible caps fail without publication. SAF enforces the cap again and verifies SHA-256 read-back as well as length. Existing collision, blocked-folder recovery, retained private partials and explicit Retry remain in use.

Local unit tests and APK/instrumentation compilation passed. Added tests cover decimal boundaries, large cap overflow, invalid durations and bounded budgeting; Android tests exercise actual under/over/impossible caps with owned media. Native execution and lint results are pending in the milestone-4 Actions run. Metadata overhead/final tag handling will be integrated in M5.

M4 Actions [42503012282](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42503012282) built the APK and passed host tests/lint. [M4 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42503012282/artifacts/13626910694). Native tests were rejected at initialization because the new cap test inferred an `Int` return from `Log.i`; corrected to `Unit`. Cap execution is included before metadata tests in the combined M4/M5 native run; no cap-device pass is claimed for this initial run.

## Milestone 5 candidate — 0.5.0 / code 7

All eleven controlled fields use structured music evidence, YouTube Topic descriptions, existing tags and confident Apple catalogue matches for missing fields only. General uploaders are not assumed to be song artists. Japanese/UK lookup is throttled, response-size bounded and timed out; ambiguous or unavailable matches leave fields unknown. Actual-container compatibility governs selected tags. Mutagen 1.47.0 is checksum pinned and bundled; WebM/Matroska uses a checked FFmpeg tag-copy fallback. Writes are made on private copies and read back; deselection removes controlled tags and old gain tags. Failed tag operations warn and retain readable audio.

ReplayGain uses FFmpeg's EBU R128 gated integrated loudness with a -18 LUFS reference and sample peak analysis; it writes per-track gain and peak, never normalizes samples or writes album gain. Opus also receives standard -23 LUFS Q7.8 R128_TRACK_GAIN. Silence/unsupported tags warn rather than inventing measurements. Analysis follows size reduction; final strict cap validation includes metadata overhead and publication verifies the full copy. Android tests compare encoded audio hashes before/after writing for every format, verify selection/removal and actual gain/peak tags, including the WebM fallback.

Local build, unit tests and lint passed; final Android native results pending. No live catalogue-match or physical-device success is claimed yet.
