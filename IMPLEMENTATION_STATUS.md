# Implementation status

Scope: milestone 1 only. Last updated: 2026-11-08.

## Implemented

- Single-activity Kotlin/Compose/Material 3 app with Home, Queue, History, Settings and dynamic/system colour themes.
- URL input with explicit clipboard Paste, HTTP(S) validation, and a blocking SAF folder-selection prompt. Persistable folder permission is requested; no media is written.
- Settings defaults from the specification, DataStore Preferences persistence, all format/quality controls, metadata switches, illustrative static Come Together tag preview and ReplayGain controls.
- Room entity/DAO/database/converters, exported schema, repository contract and enqueue-time settings snapshots.
- Shared progress/status cards, demo queue actions, history confirmation and About/license information.
- Pinned Gradle wrapper/toolchain/dependencies, build instructions and automated tests.

## Fake data and deferred work

All queue/history items and progress are explicitly **fake**. The app uses an in-memory FakeDownloadRepository; Room is defined and tested separately, not connected to the UI. Demo data resets after process death. No extraction, download, conversion, tagging, ReplayGain analysis, file saving/opening/sharing, foreground service, extractor update or Android share target is implemented. Disabled controls and the persistent demo banner make this visible.

## Verification

Completed in the managed Linux environment on 2026-11-08 using OpenJDK 21.0.13 (JDK compiler extracted from the matching official Debian security package), Android SDK 36 and Build Tools 35.0.0.

Exact final build/test command, from the repository root:

```sh
JAVA_HOME=/workspace/work/jdk21 \
ANDROID_USER_HOME=/workspace/work/android-user \
GRADLE_USER_HOME=/workspace/work/gradle-home \
GRADLE_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080' \
./gradlew --init-script /workspace/work/proxy.init.gradle \
  assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

The local init script forwards the four proxy system properties to test JVMs and sets their maximum heap to 1536 MB. It contains no credentials. This environment-specific script is outside Git; ordinary local builds use the commands in README. Test JVMs keep Robolectric's cache/lock in `app/build/test-home` via project configuration.

**Result: BUILD SUCCESSFUL (51 seconds).**

| Check | Actual evidence |
| --- | --- |
| Debug app | `assembleDebug` passed; `app/build/outputs/apk/debug/app-debug.apk` (about 13 MB) |
| Model/defaults/validation/demo snapshots | 4 JUnit tests passed |
| DataStore persistence | 1 test passed: writes all settings, closes DataStore, reopens same on-disk file and verifies values |
| Room schema | 1 Robolectric API 35 test passed: entity/UUID/enums/settings round-trip and atomic history deletion |
| Compose UI | 3 Robolectric API 35 tests passed: four-tab navigation/history confirmation, invalid URL/folder prompt, metadata toggle/illustrative preview |
| Lint | `lintDebug` passed: 0 errors, 18 warnings; mainly newer dependency releases, plus resource access, backup-rule recommendation, KAPT recommendation and missing explicit launcher icon |
| Device-test APK | `assembleDebugAndroidTest` passed; device navigation smoke test compiled, **not executed on device** |

Reports are generated under `app/build/test-results/testDebugUnitTest/`, `app/build/reports/tests/testDebugUnitTest/` and `app/build/reports/lint-results-debug.html`. Room schema v1 is checked in under `app/schemas/`.

Earlier environment-only attempts failed because Java initially lacked a compiler, the wrapper needed explicit Java proxy properties, and Robolectric attempted a lock in a read-only home directory. These were resolved; the final full command passed. Pinned versions are verified working together, not claimed to be the newest releases.

**No physical device or emulator verification was performed.** Robolectric provides simulated Android verification, not device testing. SAF picker/permission retention, TalkBack, font scaling and minimum-API hardware behavior remain manual checks. There are no milestone-1 build/test blockers; real extraction, media, and background acceptance tests remain unverified and out of scope.

## Next milestone

Milestone 2: verify/pin compatible yt-dlp and FFmpeg integrations (including licenses/ABIs), inspect and download one real audio item in Original mode, and save it through SAF. Do not start it without the user's instruction.
