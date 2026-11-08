# Implementation status

Requested scope: milestones 2 and 3 of design v2. Milestone 1 is complete. **Milestone 2 code is implemented, but its live YouTube acceptance gate is blocked. Milestone 3 has not started; an explicit gate override/device verification is pending.** No full parity or successful live download is claimed.

## Milestone 2 implementation

- Strict canonical YouTube watch/Shorts/youtu.be parsing, single-item/live/collection validation, per-source+kind duplicate reservations and complete enqueue snapshots.
- Room-backed FIFO/history and monotonic order; one foreground worker; notification/cancel; bounded partial wake lock; interrupted recovery, explicit Resume/Retry, failure continuation and reservation release.
- Separate SAF folders, per-type first selection, revoked-folder pause, explicit replacement of matching waiting jobs only.
- Pinned youtubedl-android 0.18.1 Python/QuickJS/yt-dlp, FFmpeg and ffprobe. Structured subprocess arguments, per-command session/handshake, group cancellation, bounded output/deadlines, coroutine cancellation and output draining.
- Best-source audio with stream-copy demux where needed; compatible real H.264/AAC MP4 selection and ffprobe checks; private staging; Unicode-safe title names/optional URL suffix; verified SAF copy; collisions skipped; incomplete destination deletion on failure.
- Source errors surface clearly. Authentication is explicitly unsupported; no cookie import, client impersonation or bypass is added.
- Audio encoding/resolution settings are explicitly blocked until M3. Type-specific size caps are blocked until M4. Metadata/ReplayGain remain M5 warnings; existing tags may remain. No fake repository is wired into the production app.

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

**Final local command: BUILD SUCCESSFUL, 47 seconds, all 20 JVM/Robolectric tests passed.** Coverage: settings/defaults/persistence, strict parsing and spoof/collection/live rejection, names/Unicode, format preference availability, input/mode/draft behavior, Room snapshots and duplicate reservations, FIFO/failure/folder recovery, non-destructive schema 1→2 history migration, and safe error classification. Scheduling tests use explicit fakes, not native output. Lint passed with no errors; warnings remain. App and instrumentation APKs compiled. Reports are under `app/build/reports/` and `app/build/test-results/`.

## Actual Android emulator / live evidence

GitHub-hosted Android 10 / API 29, x86_64:

- [Run 42480384765](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42480384765): real native FFmpeg/ffprobe **7.1.1**, bundled yt-dlp **2025.11.12** executed; owned H.264/AAC fixture validated; process-group descendant cancellation passed. Initial live test stopped at an Android-10 DocumentsUI label mismatch, corrected in test code.
- [Run 42481332660](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42481332660): CI build/JVM tests passed. Real DocumentsUI grant succeeded; the old yt-dlp fixture `BaW_jenozKc` returned “This video is unavailable”. No live saving success.
- [Run 42482139097](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42482139097): CI build/JVM tests passed; owned native media/cancellation passed again. The attributed CC Blender fixture `aqz-KE-bpKQ` failed with **“Sign in to confirm you're not a bot”**. Both requested jobs failed instead of falsely completing. No authentication bypass was attempted.
- Independent real SAF fixture/collision/cancellation tests have now been added and compiled; the updated emulator run is pending. The known-blocked live case is explicitly opt-in and will be reported separately, not counted as passing when skipped.

No physical phone testing was performed. Local emulator installation/startup was attempted; absent KVM and the workspace disk budget prevented startup, so unused images were removed and GitHub-hosted KVM was used. Other ABIs, 16 KB page-size devices, screen-lock/OEM behavior and full recovery/publication hardening are not verified.

## Acceptance blocker / next action

Design §15 M2 says **“Test real audio AND video before proceeding.”** Its intended YouTube extraction/save gate remains unmet because CI is asked to authenticate, while the design excludes login/cookies/evasion. The owner has been asked whether to proceed with M3 implementation while explicitly recording this live-verification limit, or pause M3 for device verification. No elapsed time or preselected choice is treated as approval.

Current app: **0.2.0 / code 3**, stable package `com.barebonesdownloader.android`. Debug identity uses the existing cached key; cache eviction remains a signing-continuity limitation. [Actions APKs/reports](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml), [native provenance/licenses](docs/NATIVE_RUNTIME.md), [parity map](docs/DESKTOP_PARITY.md). Native GPL distribution obligations apply; this remains the owner's private personal-use build.
