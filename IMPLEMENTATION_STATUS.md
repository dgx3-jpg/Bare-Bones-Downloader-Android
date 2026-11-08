# Implementation status

Requested scope now includes **all milestones 1–6** of design v2. M1–3 were implemented previously; M4–6 code is now implemented in candidate **0.6.0 / code 8**. M4/M5 native verification passed; final M6 native verification is in progress. This is not a full live-site/device parity claim.

## Current implementation

| Milestone | Implemented behavior | Actual evidence / limits |
| --- | --- | --- |
| M1 | Compose utility UI, five draft settings sections, DataStore/Room, themes/icon and APK workflow | Existing UI/model/persistence/migration tests; see native limits below |
| M2 | Strict YouTube parser, native extraction/processing, persistent FIFO, per-kind reservations, foreground service and SAF | Native H.264/AAC, process descendant cancellation, real SAF grants/read-back/collision/cancel cleanup passed. Live YouTube gate remains blocked by site verification. Owner explicitly authorized continuing with this limit. |
| M3 | Nine formats, copy/explicit quality/bitrate, seven video height ceilings, filename rules and actual codec checks | Android 10/x86_64 decoded 22 real default/variable/bitrate outputs plus compatible-copy/re-encode assertions. |
| M4 | Strict decimal-MB caps, seven bounded reductions, selected-lossy/AAC fallback, final tag overhead, publication hash checks | Actual cap tests passed in run 42504090820: audio 256807 bytes, video 923568 bytes, each strictly below 1000000; under-cap unchanged and impossible cap failed. Finalized tags/gain reapply after each size attempt. |
| M5 | All eleven fields, source/Topic/existing/confident-Apple missing-only resolution, selected removal and read-back; per-track ReplayGain | Unit confidence/precedence/reference tests passed. Initial Android WAV/AAC/ALAC tag/RG round-trips and unchanged encoded hashes passed; FLAC exposed a Vorbis comment iteration bug, fixed in b105d70. Corrected Android rerun 42505013422 passed all nine format and WebM tag/removal/RG round-trips, with unchanged encoded hashes. |
| M6 | Other public single-item sources with namespace IDs, share prefill, persistent diagnostics, reservation rebuild, SAF recovery journal, cancellation race protection, signing guard and update test | Local 35 JVM/Robolectric tests, APK/instrumentation compilation and lint passed. Owned HTTP screen-off FIFO, share/journal, scaled theme screenshots, actual old/new APK update and opt-in TikTok checks added; final Android execution pending. |

Public sources are inspected for a single accessible non-live/non-DRM item; collections/auth/private content fail explicitly. YouTube keeps its stricter URL rules. Generic MP4 with unknown codec labels must pass actual H.264/AAC/height checks before cap conversion. Uploaders are not automatically music artists. Metadata/ReplayGain failures warn and keep readable audio. Silent/too-short audio can have no valid gain. No cookie/login/bypass, live recording, player, analytics or server is added.

## Verification record

- [M3 run 42501942123](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42501942123), source 1f0d04c: build/23 host tests/lint passed; four native tests passed, one live test failed. Actual yt-dlp **2026.10.26**, FFmpeg/ffprobe **7.1.1**, API29/x86_64. Live Blender CC fixture returned **“Sign in to confirm you're not a bot”** for audio and video; no live save success claimed. Earlier extractor runs had the same restriction.
- [M4 run 42503012282](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42503012282), source 8249ced: host build/tests/lint and [M4 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42503012282/artifacts/13626910694) passed. Native suite initially rejected a cap test's inferred Int return; fixed to Unit in M5. Actual cap evidence is in the following run.
- [Initial M5 run 42504090820](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42504090820), source 04425ce: build/30 host tests/lint passed; [M5 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42504090820/artifacts/13626777881). Native report: 7 methods, 5 passed, 1 failed (FLAC tag iterator), 1 opt-in live skip. Caps passed; tag bug fixed rather than treating warnings as success.
- [M5 corrected native rerun 42505013422](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42505013422), source b105d70: build/30 host tests/lint and native suite passed (6 passed, 1 opt-in live skip). [Corrected M5 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42505013422/artifacts/13626893718).

Local toolchain: OpenJDK21.0.13, SDK36, Build Tools35.0.0, Gradle8.13, AGP8.13.2, Kotlin2.2.21. Commands: `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`, followed by `./gradlew lintDebug`. Host scheduling tests use explicit fakes; native and SAF tests use real Android binaries/providers. Host tag checks use host FFmpeg only and do not substitute for Android results.

## Known acceptance / release limits

- Live YouTube is blocked by site verification in CI. TikTok/public-site results and final M6 evidence will be recorded below. No full website success guarantee.
- Live Apple catalogue success is not independently verified; selection/confidence logic and actual known-tag round-trips are tested. Missing/ambiguous fields stay unknown.
- No physical phone, ARM runtime, 16 KB page-size device or OEM battery-policy verification is claimed. Native evidence is Android 10/API29 x86_64. Accessibility beyond recorded scaled-theme checks remains manual.
- SAF has no universal atomic/exclusive publication. Handled failures remove new documents; the journal supports process-death cleanup. Lost grants/delete refusal and the small provider-create/journal-commit transaction gap can require manual folder cleanup.
- Established certificate is pinned and CI refuses a missing/different key. An optional private Actions secret supports restoring the existing key; no durable backup is asserted configured. Cache loss requires owner restoration. No private key is committed or put in artifacts.
- GPL corresponding-source/combined-license duties apply before wider redistribution. This remains the owner's private personal-use build.

[Native provenance](docs/NATIVE_RUNTIME.md), [parity map](docs/DESKTOP_PARITY.md), [release/update checklist](docs/RELEASE_CHECKLIST.md), [Actions artifacts](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml).

## Signing blocker found during final M6 verification

Run [42505873785](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42505873785) stopped before building because the established signing key was absent. Inspection confirmed zero matching caches; earlier build logs reported “Path(s) specified ... do(es) not exist”, so the legacy cache never retained the key. The old APK alone cannot recover its private signing key. The owner has been asked to restore a private backup through the Actions secret, or explicitly accept a new stable identity and clean install. No incompatible APK was published.

Signing now uses an explicit `BBD_DEBUG_KEYSTORE_PATH` shared by Gradle, the guard and cache. A separate `verification_only` workflow can run all independent tests with an isolated seven-day key; it skips APK publication, stable-key caching and the in-place update test. This does not resolve the old-key continuity blocker or assert a new stable key was approved.

## First M6 isolated Android run

[42506521028](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42506521028), source 0cc1671, used a temporary verification key and published no APK. Build, 35 host tests and lint passed. Native report: 11 methods, 8 passed, 2 failed, 1 live-YouTube skip.

- Actual generic HTTP extraction completed Audio A / Video A / Video B through the real FIFO, foreground service, FFmpeg, metadata removal and SAF while the emulator screen was off.
- Final tag-overhead test passed: 229388 < 300000 bytes, a 200000-character comment retained, and ReplayGain independently matched the final AAC samples.
- All codec/cap/tag/RG/SAF/cancellation checks passed again. Scaled light/dark UI assertions passed; the first screenshot export ran after Gradle removed the app, so those exported files were invalid and are not visual evidence. Export now uses dedicated shell-owned emulator test files before uninstall.
- Share prefill/no-enqueue and journal assertions executed, but ActivityScenario teardown failed after the shared intent changed the monitored launch intent. The test now delivers via Instrumentation and restores its original monitor intent before teardown.
- The real TikTok probe failed: **“Your IP address is blocked from accessing this post.”** No successful TikTok audio/video extraction is claimed, and no bypass was attempted. A specific SOURCE_RESTRICTED user-facing error is added. The corrected independent suite skips this known-restricted live probe; the failure above remains the live evidence.
- In-place update verification was explicitly skipped because the established private key is unavailable. The owner signing choice remains pending.
