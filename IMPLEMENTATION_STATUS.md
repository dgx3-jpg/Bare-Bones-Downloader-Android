# Implementation status — design v2

Scope: updated **milestone 1 only**, 2026-11-08. The earlier audio-only shell has been revised to match the uploaded v2 design. Milestones 2–6 have not been started.

## Changes and working shell features

- Main utility screen replaces four bottom-navigation tabs: Audio/Video mode switch, per-type destination, multiline input, Queue audio/Queue video, Cancel current, Clear waiting, URL/Type/Status placeholder cards and collapsible/copyable diagnostics capped at 60,000 characters. Switching mode preserves input and queued data; fresh ViewModel launches default to Audio.
- General, Audio, Video, Metadata, Appearance settings with isolated draft, Save and Cancel/back. Real DataStore persistence, normalization/corruption fallback, two SAF folder preferences, human-readable names, persistable grants and changed-folder write/create checks. Native picker/provider behavior is not device-tested.
- Nine audio format preferences, format-dependent quality controls, quality 0–10 / bitrate 32–512, independent size caps and seven video height choices. No encoder claim.
- All 11 metadata selections default ON; static Here Comes the Sun / The Beatles / Abbey Road preview; format-specific Unavailable labels. ReplayGain preference defaults ON and is explicitly not implemented.
- Explicit persisted Light/Dark, desktop palette and SVG-derived Penrose adaptive icon. Android 10+; package `com.barebonesdownloader.android`, display name Bare Bones Downloader, version 0.1.1/code 2.
- Room schema includes kind, destination and complete immutable settings snapshots; database round-trip verified. Demo UI stays in-memory, not a persistent or functional download queue.
- GitHub Actions workflow tests/builds debug APK and uploads `app-debug-apk` plus reports. See README for artifact download and signing caveats.

## Verification

Local toolchain: OpenJDK 21.0.13, SDK 36, Build Tools 35.0.0; pinned Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21.

Exact final local command from the repository root:

```sh
JAVA_HOME=/workspace/work/jdk21 \
ANDROID_USER_HOME=/workspace/work/android-user \
GRADLE_USER_HOME=/workspace/work/gradle-home \
GRADLE_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080' \
./gradlew --init-script /workspace/work/proxy.init.gradle \
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The local init script only forwards proxy properties and sets test heap size to 1536 MB. Test JVM caches/locks use `app/build/test-home`. Ordinary local commands are in README.

**BUILD SUCCESSFUL in 1m 3s. All 12 tests passed:**

| Coverage | Actual result |
| --- | --- |
| V2 defaults, corruption/normalization, supported quality modes, preview compatibility, shell HTTP validation, full per-type settings snapshots | 6 JUnit tests passed |
| Save/Cancel isolation, mixed-input retention, mode-specific destination gate, log bound | 1 ViewModel test passed |
| All v2 settings survive closing/reopening real on-disk DataStore | 1 test passed |
| Room schema/entity/type/destination/complete settings round-trip | 1 Robolectric API 35 test passed |
| Mode/input/picker-prompt cancellation, five settings sections, Save/Cancel/theme persistence, metadata preview toggles | 3 Robolectric API 35 UI tests passed |
| `assembleDebug` | Passed: `app/build/outputs/apk/debug/app-debug.apk` |
| APK identity/signature | `aapt dump badging app/build/outputs/apk/debug/app-debug.apk` verified ID/label/version/minSdk 29/target 36; `apksigner verify --verbose` passed with APK Signature Scheme v2 |
| `assembleDebugAndroidTest` | Passed: instrumentation smoke test compiled; not executed on device |
| `lintDebug` | Passed: 0 errors, 20 warnings (mostly dependency updates; also KAPT, backup rules, icon/resource recommendations and a KTX suggestion). |

The first revised shell build also passed. One test initially targeted an off-screen Appearance tab without scrolling; the test was corrected and the full suite passed. No assertion was removed. Local reports: `app/build/test-results/testDebugUnitTest/`, `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-debug.html`.

**GitHub Actions verified:** [run 42476569402, attempt 2](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42476569402/attempts/2) completed successfully for code/workflow commit `fc584718f5c729ca6c8e018be9132e7b660bc813`. Hosted Ubuntu runner, Temurin JDK 17.0.21; `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL in 4m 36s**.

- [Download app-debug-apk](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42476569402/artifacts/13615906765): confirmed uploaded, 11,466,165-byte ZIP containing `app-debug.apk`.
- [Verification reports](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42476569402/artifacts/13616125834): confirmed uploaded.
- The initial workflow exposed missing `sdkmanager` on PATH; explicit pinned Android SDK provisioning fixed it. The next attempt encountered a compiler dependency download timeout; one failed-job retry succeeded without source changes. Debug signing cache post-step succeeded.
- This evidence update changes documentation only; it does not alter the verified app or workflow.

## Limits and remaining work

No physical device/emulator verification performed. Robolectric is simulated Android testing. Native SAF provider behavior, actual adaptive launcher rendering, TalkBack/scaled type and minimum-API device behavior remain manual checks. No real downloads, metadata writes, ReplayGain scans, transcoding, codec/size validation, strict YouTube extraction/duplicates, staging/publication or background service exists. All queue/progress/log download activity is clearly labelled placeholder data.

The v2 package ID is different from the v1 draft; that draft cannot update in place into v2 or transfer its preferences automatically. V2 identity/schema is the baseline from here. Local signing remains stable while its debug key is preserved. Actions caches its debug key, but cache eviction changes that identity; durable update-safe signing remains M6. No keys or secrets are committed or included in APK artifacts.

Desktop reference mapping and test limits: [docs/DESKTOP_PARITY.md](docs/DESKTOP_PARITY.md). No claim of full desktop parity.

## Next action

Stop after M1. On the user's next instruction, M2 is real YouTube audio AND H.264/AAC video extraction, FIFO/duplicate reservations, separate SAF destinations, staged saving/cancellation and foreground service; verify actual audio/video before moving on.
