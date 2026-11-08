# Signing repair — 0.6.1 / code 9

The owner instructed the assistant to fix the remaining problems. A new permanent signing identity and private backup have been created. Its public SHA-256 is `80bd2bc24c4cf2e4710089a66bbb4eeba6323f840b913349936f729887696091`. Older CI APKs need one clean install because their private keys are lost; no data on the owner's device has been removed.

Release signing now happens offline with source binding and public hash receipts. CI verifies signed APK assets and can test/publish them without access to the private key. All permanent-key caches are removed; source/PR checks use non-published temporary keys. Optional Actions-secret signing remains supported for owners with administrative access. The signed 0.6.1 APK was built and verified locally from `d048cda5222e36dbd3ec71b6ef5726387ff4490f`; 36 host tests, lint and assembly passed. APK SHA-256: `9e208180e082bc527277124e06c982e9c05c7866deaa893ed34e80fab528b1ff`. Direct workspace delivery is available. The GitHub release upload returned HTTP 400; the empty candidate release was removed. Permanent-key device upgrade verification remains unexecuted because these signed assets could not be uploaded. CI is independently testing the same update fixture using temporary same-certificate APKs in run 42520915025. No temporary-key installable APK is published.

Latest official yt-dlp release was rechecked on 2026-11-08: **2026.10.26**, already pinned. No newer official extractor fix is available to address the recorded YouTube sign-in/TikTok IP restrictions. These site-side restrictions are not reported as repaired or bypassed.

---

## Historical milestone implementation and verification record

The following records describe the earlier 0.6.0 candidate and its then-current signing blocker. The signing-repair section above supersedes key-approval and delivery statements below.

Requested scope includes **all milestones 1–6** of design v2. **M4 and M5 are complete and verified. M6 code and independent Android checks are verified; its final APK publication and in-place update check are blocked by the missing prior CI signing key.** Candidate **0.6.0 / code 8**, package `com.barebonesdownloader.android`. The owner signing decision remains pending; no new permanent identity has been approved or published. This is not a full live-site/physical-device parity claim.

**Final independent verification:** [green run 42512639926](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42512639926), source `67aabbf9536d87a6d4b51c5ff89e41f6da5efb2a`: **36 host tests passed, lint and APK/instrumentation assembly passed; 9 actual Android methods passed, 2 live checks explicitly skipped, 0 failures/errors.** [Native XML/logs/screenshots](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42512639926/artifacts/13630356702), [host reports](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42512639926/artifacts/13629329058). The verification key was isolated; no M6 APK was published from this run.
## Current implementation

| Milestone | Implemented behavior | Actual evidence / limits |
| --- | --- | --- |
| M1 | Compose utility UI, five draft settings sections, DataStore/Room, themes/icon and APK workflow | Existing UI/model/persistence/migration tests; see native limits below |
| M2 | Strict YouTube parser, native extraction/processing, persistent FIFO, per-kind reservations, foreground service and SAF | Native H.264/AAC, process descendant cancellation, real SAF grants/read-back/collision/cancel cleanup passed. Live YouTube gate remains blocked by site verification. Owner explicitly authorized continuing with this limit. |
| M3 | Nine formats, copy/explicit quality/bitrate, seven video height ceilings, filename rules and actual codec checks | Android 10/x86_64 decoded 22 real default/variable/bitrate outputs plus compatible-copy/re-encode assertions. |
| M4 | Strict decimal-MB caps, seven bounded reductions, selected-lossy/AAC fallback, final tag overhead, publication hash checks | Actual cap tests passed in run 42504090820: audio 256807 bytes, video 923568 bytes, each strictly below 1000000; under-cap unchanged and impossible cap failed. Finalized tags/gain reapply after each size attempt. |
| M5 | All eleven fields, source/Topic/existing/confident-Apple missing-only resolution, selected removal and read-back; per-track ReplayGain | Unit confidence/precedence/reference tests passed. Initial Android WAV/AAC/ALAC tag/RG round-trips and unchanged encoded hashes passed; FLAC exposed a Vorbis comment iteration bug, fixed in b105d70. Corrected Android rerun 42505013422 passed all nine format and WebM tag/removal/RG round-trips, with unchanged encoded hashes. |
| M6 | Other public single-item sources with namespace IDs, share prefill, persistent diagnostics, reservation rebuild, SAF recovery journal, cancellation race protection, signing guard and update test | 36 host tests/lint/assembly and 9 independent Android checks passed. Actual owned HTTP screen-off FIFO, share admission/prefill without enqueue, publication-journal cleanup/protected bytes and scaled light/dark screenshots verified. Actual old/new APK update remains blocked/skipped because the prior private signing key is unavailable. TikTok live probe was IP-restricted. |

Public sources are inspected for a single accessible non-live/non-DRM item; collections/auth/private content fail explicitly. YouTube keeps its stricter URL rules. Generic MP4 with unknown codec labels must pass actual H.264/AAC/height checks before cap conversion. Uploaders are not automatically music artists. Metadata/ReplayGain failures warn and keep readable audio. Silent/too-short audio can have no valid gain. No cookie/login/bypass, live recording, player, analytics or server is added.

## Verification record

- [M3 run 42501942123](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42501942123), source 1f0d04c: build/23 host tests/lint passed; four native tests passed, one live test failed. Actual yt-dlp **2026.10.26**, FFmpeg/ffprobe **7.1.1**, API29/x86_64. Live Blender CC fixture returned **“Sign in to confirm you're not a bot”** for audio and video; no live save success claimed. Earlier extractor runs had the same restriction.
- [M4 run 42503012282](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42503012282), source 8249ced: host build/tests/lint and [M4 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42503012282/artifacts/13626910694) passed. Native suite initially rejected a cap test's inferred Int return; fixed to Unit in M5. Actual cap evidence is in the following run.
- [Initial M5 run 42504090820](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42504090820), source 04425ce: build/30 host tests/lint passed; [M5 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42504090820/artifacts/13626777881). Native report: 7 methods, 5 passed, 1 failed (FLAC tag iterator), 1 opt-in live skip. Caps passed; tag bug fixed rather than treating warnings as success.
- [M5 corrected native rerun 42505013422](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42505013422), source b105d70: build/30 host tests/lint and native suite passed (6 passed, 1 opt-in live skip). [Corrected M5 APK](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42505013422/artifacts/13626893718).

Local toolchain: OpenJDK21.0.13, SDK36, Build Tools35.0.0, Gradle8.13, AGP8.13.2, Kotlin2.2.21. Commands: `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`, followed by `./gradlew lintDebug`. Host scheduling tests use explicit fakes; native and SAF tests use real Android binaries/providers. Host tag checks use host FFmpeg only and do not substitute for Android results.

## Known acceptance / release limits

- Live YouTube is blocked by site verification in CI. TikTok was actually probed and rejected the runner IP; final independent M6 evidence is recorded above/below. No full website success guarantee.
- Live Apple catalogue success is not independently verified; selection/confidence logic and actual known-tag round-trips are tested. Missing/ambiguous fields stay unknown.
- No physical phone, ARM runtime, 16 KB page-size device or OEM battery-policy verification is claimed. Native evidence is Android 10/API29 x86_64. Accessibility beyond recorded scaled-theme checks remains manual.
- SAF has no universal atomic/exclusive publication. Handled failures remove new documents; the journal supports process-death cleanup. Lost grants/delete refusal and the small provider-create/journal-commit transaction gap can require manual folder cleanup.
- The replacement permanent certificate is pinned. Its private backup is delivered separately to the owner; the owner must retain it outside this session. Older lost-key APKs require one clean install. CI temporary keys are restricted to verification; offline release assets require certificate/hash/source verification. No private key is committed, cached, or put in GitHub artifacts.
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

## Recovery and runner follow-up

Run [42507714105](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42507714105) passed its build/35 host tests/lint. Instrumentation completed all 11 methods: one journal-cleanup assertion failed, two live checks were skipped, and the remaining assertions (including share delivery, screen-off FIFO, cap/tag overhead and scaled themes) passed. Gradle UTP then hung in post-test `am get-current-user` / additional-output collection and reached the job timeout; this is not a green native run. Partial raw instrumentation evidence is retained in its artifact.

The journal no longer equates `DocumentFile.exists()==false` with successful removal: that helper can hide permission/provider failures. It directly requests deletion of the recorded owned URI, retains the marker on failure and preserves already-completed publications. A real-file JVM test checks uncommitted versus completed records and denied-delete retention; **36 host tests pass**. Native revalidation remains pending.

The test script now runs real `am instrument` directly, records its actual per-method status callbacks into JUnit XML, rejects incomplete/zero-test reports and bounds transport cleanup. Its parser was checked against the actual failed instrumentation transcript and reported exactly one failure/two skips. This avoids UTP's hung post-test collector; it does not replace Android execution with mock results. Theme captures use UiDevice and app-private test files. The direct driver retains the test installation for export; host PNG signatures are checked so a run-as error cannot be mistaken for an image. The final XML and valid PNGs were checked in run 42512639926.

Run [42511387482](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42511387482), source ca45e82, completed with the direct driver: build/36 host tests/lint passed; native 11 methods, one assertion failure, two live skips. The remaining failure used `DocumentFile.exists()` on an Android-10 Downloads `raw:` URI after successful deletion. Provider diagnostics showed the deletion completed, while that convenience document query continued returning a row. The recovery assertion now checks the actual parent listing and inability to read the removed file, and reads back the protected completed document's exact bytes.

All other native checks passed. Two valid light/dark PNGs at 1.3 font scale were exported and visually inspected: titles wrap within the app bar, labels/inputs/buttons remain readable and the page scrolls. Added spacing between radio icons and labels. Source-aware music parsing also uses the strict YouTube host boundary, preventing a lookalike suffix such as `notyoutube.com` from receiving Topic-specific rules. These final corrections are locally built and tested; final Android rerun follows.

## Final M6 independent acceptance evidence

Run **42512639926** used Android 10/API29, x86_64, actual yt-dlp **2026.10.26** and FFmpeg/ffprobe **7.1.1**. Direct instrumentation report: **11 methods = 9 passed + 2 explicitly skipped live checks**, zero failures/errors.

Passed native checks:

- H.264/AAC fixture, actual binary versions and descendant cancellation.
- Real DocumentsUI grant, SAF verified copy, collisions and cancelled-copy cleanup.
- Nine audio formats / supported quality-bitrate modes; default-compatible copy versus explicit encode; actual codecs, containers and decode checks; real video-height ceiling.
- Strict audio/video caps, under-cap no re-encode, impossible cap failure. Final tag overhead case: **229388 < 300000 bytes**, full 200000-character comment retained and gain independently remeasured from final AAC.
- Selected fields and deselected removal across all nine choices plus WebM; actual per-track gain/peak tags and unchanged encoded audio hashes.
- Share MIME resolution and onNewIntent delivery prefill input without enqueue. Recovery verifies the incomplete document is absent from its parent/unreadable, while the completed document's exact bytes survive. No unreliable raw-URI exists query is used as deletion evidence.
- Actual generic HTTP extraction of owned generated media, Audio A / Video A / Video B through the production FIFO/foreground service/processing/SAF while the emulator screen is off. Local URL admission is bypassed only by explicit test seeding; production still rejects localhost URLs.
- Light/dark at 1.3 font scale. Both valid exported PNGs were visually inspected; labels, fields and buttons remain readable, title wrapping stays within the app bar and scrolling works.

The two skipped tests are not download successes: YouTube was previously blocked by sign-in verification (run 42501942123); the real TikTok probe was rejected with **“Your IP address is blocked from accessing this post”** (run 42506521028). Neither was bypassed.

## Final release blocker / required owner input

A normal release-mode recheck, [run 42513349402](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/runs/42513349402), failed only at the signing guard: **“Established debug key is unavailable.”** No existing-key secret or v2 cache was available. My earlier cache path did not match the build's actual keystore path, so the old key was not retained; this was an implementation mistake. The explicit path is now corrected, but an APK's public certificate cannot recover its private key.

To finish APK delivery/update verification, the owner must either restore the matching private backup through Actions secret `BBD_DEBUG_KEYSTORE_BASE64`, or explicitly accept a new permanent identity and a clean install (settings/history reset). Do not send private keys in chat. The connected integration cannot administer repository secrets (HTTP403); restoration is through repository Settings → Secrets and variables → Actions. No new permanent key, incompatible-update APK, or destructive reinstall has been performed.

The independent software work is complete. The final M6 APK, durable identity decision and actual in-place update acceptance remain blocked on that input. Physical-device/ARM/16KB/OEM/live-catalogue checks remain the documented release limitations.
