# Bare Bones Downloader for Android — Codex Implementation Specification

**Version:** 2.0 — 8 November 2026  
**Product:** Standalone Android port of the existing Bare Bones Downloader, not an audio-only redesign  
**Desktop source of truth:** https://github.com/dgx3-jpg/BareBonesDownloader  
**Audited desktop revision:** tree `e53be3c92d8697b2acb1c5974c1650b473dc9261` (default branch at inspection).  
**Delivery:** Sideloadable debug APK after every milestone, plus GitHub Actions artifact.  

## 0. Codex operating rules

1. **Implement; do not re-plan.** This document fixes product choices. Do not produce competing UI/architecture proposals. Only ask about genuine blockers (licensing, unavailable compatible native libraries, or security-sensitive credentials).
2. Desktop code is the behavioral reference for **audio AND video**, including settings and visual identity. Inspect the files in §1 before implementing their counterparts. If a feature here conflicts with the earlier audio-only Android draft, **this v2 document wins**.
3. Do not copy WinForms or Windows executables to Android. Reimplement behavior natively. Reuse algorithms/validation rules where appropriate, not C# GUI code.
4. Work through milestones sequentially; stop after the requested milestone. Each milestone must compile, run tests, produce an installable APK, and update `IMPLEMENTATION_STATUS.md` with actual evidence and unresolved issues. Never claim device tests were run if they were not.
5. Use a single sequential FIFO download worker. Preserve the queued job's type, destination and complete settings snapshot; later settings edits must not silently change queued jobs.
6. No server, accounts, analytics, advertisements, subscriptions, sync, embedded browser, DRM circumvention, or music player. This is a **separate** app from the Windows version.
7. Prefer maintained, license-compatible libraries. Pin tested versions. Do not assume abandoned FFmpegKit distributions are maintained. If ReplayGain 2.0 tagging cannot be supported by an available Android-compatible build, record the precise blocker and do not substitute volume normalization or invent tags.
8. Limit scope changes: platform-specific additions are Android share intents, background foreground-service handling, storage picker integration and APK distribution. Avoid speculative features.

## 1. Verified desktop baseline and reference map

The desktop repository is a C#/.NET Framework WinForms app, not merely a command-line wrapper. The current README and source establish these capabilities:

| Source | Required parity |
|---|---|
| `src/DownloaderForm.cs` | Audio/video mode switch; multiline URL input; Queue audio / Queue video; FIFO queue with URL, Type, Status; Cancel current; Clear waiting; diagnostics log; folder change and blocked-folder recovery; per-job settings snapshots; no-overwrite publishing; duplicate reservation by source ID + mode |
| `src/YoutubeUrl.cs` | Strict YouTube single-video URL parsing; watch, Shorts, youtu.be; strip playlist context; reject playlist-only, channel and malformed URLs |
| `src/SettingsForm.cs`, `src/SettingsStore.cs` | Five settings sections: General, Audio, Video, Metadata, Appearance; separate destinations; filename URL suffix toggle; audio format and encoding; audio/video size caps; video height cap; 11 metadata checkboxes and live Beatles sample; ReplayGain toggle; Light/Dark |
| `src/AudioFormats.cs`, `src/AudioEncoding.cs` | Best available, AAC, ALAC, FLAC, M4A, MP3, Opus, Vorbis, WAV; default/copy, variable-quality, explicit bitrate; format-dependent supported controls |
| `src/MusicMetadata.cs`, `src/OnlineMetadata.cs` | Source structured music metadata → Topic description → existing tags → confident Apple catalogue match (Japanese/UK region, title/artist/duration); only fill missing fields; selected tags written, deselected tags removed; warnings not fatal |
| `src/MediaSizeLimiter.cs` | Independent optional decimal-MB caps for audio and video; skip re-encode below cap; bounded bitrate-reduction retries above cap; verify final size; fail impossible cap without publishing |
| `src/VideoOutput.cs` | MP4 video with real H.264 video and AAC audio; ffprobe validation; cover art does not count as video |
| `src/DownloadFiles.cs`, `src/ProcessJob.cs` | Staging/work files, safe publication, collision skip, cancellation of subprocess tree, retain recoverable partials |
| `src/AppTheme.cs`, `assets/penrose-white-grey.svg` | Native sparse utility UI; light and dark palettes; existing white/grey/cyan Penrose visual identity |
| `tests/*` | Regression expectations for parsing, encoding, metadata lookup, Unicode, safe output and queue handling |

**Important baseline distinction:** The current Windows app accepts **YouTube video URLs only** and explicitly excludes live streams, playlists, sign-in and cookies. TikTok/other yt-dlp sites are an **Android extension**, not existing desktop parity. Keep their handling separately specified and tested; do not describe them as working desktop features.

### Parity precedence

1. Core desktop behavior above is mandatory.
2. Android additions in this document are mandatory where marked.
3. Where a source-specific feature cannot apply (e.g. YouTube music catalogue data for TikTok), use a conservative fallback and disclose missing metadata; never fabricate artist/album.

## 2. Fixed Android technology and packaging

- Kotlin, single-activity Jetpack Compose, Material 3 components with restrained custom styling, MVVM, Coroutines/Flow.
- Room for queue rows and job history, DataStore for persistent preferences. Desktop queue/log are session-only, but Android queue persistence is intentionally added to survive process death.
- Android-compatible embedded yt-dlp execution (evaluate `yausername/youtubedl-android` and pin compatible version), with compatible Python runtime and required extraction dependencies. Do not invoke Windows `.exe` tools.
- Android-compatible FFmpeg/ffprobe binaries or maintained compatible libraries for extraction, codec conversion, size reduction and media validation. Verify codecs `aac`, `alac`, `flac`, `libmp3lame`, `libopus`, `libvorbis`, `pcm_s16le`, `libx264` (or an equivalent H.264 encoder), plus MP4 muxing before claiming parity. If an encoder is unavailable, explicitly block that option rather than pretending to support it.
- Metadata tag writer supporting ID3, Vorbis/FLAC, MP4 atoms, Opus and relevant formats; test actual written tags. Source-aware compatibility matrix must be enforced (MP4 source URL unsupported; WAV composer/source URL/album artist unsupported, matching desktop).
- Android 10+ (`minSdk 29`), compile/target SDK at current supported stable versions pinned in Gradle. No broad all-files permission; use SAF folder grants and MediaStore where appropriate.
- Single `app` module with packages `ui`, `data`, `download`, `media`, `metadata`, `settings`, `storage`; no unnecessary multi-module refactor.
- Package ID `com.barebonesdownloader.android`; display name **Bare Bones Downloader**. Keep stable application ID and debug signing across milestones to permit in-place installs.
- GitHub Actions on push/PR: assembleDebug, unit tests, publish `app-debug.apk` artifact. Never commit private signing keys or secrets. Debug builds are sufficient for personal sideloading.

## 3. Visual design — faithful mobile adaptation

**Do not use the previous proposed album-art music-player UI.** The desktop app is a compact utility: download mode at top, destination, links input, queue, log and settings. Mobile should retain that order and terminology.

### Main screen, portrait

```text
Bare Bones Downloader                        [gear]
Download as
[ ● Audio only      ○ Video + audio ]
Audio format and size from Settings.
Audio folder                     [Change…]
[ Music / selected folder               ]
Links (one per line)
[ Paste one or more links                ]
[                                       ]
[ Queue audio ]
[ Cancel current ]    [ Clear waiting ]
Queue
[ URL/title                   Audio only ]
[ Downloading                            ]
[ URL/title                Video + audio ]
[ Waiting                                ]
Log                                   [Copy]
[ timestamped diagnostic lines ...        ]
```

- On mode change: helper text, folder label and primary action change immediately; input and queued rows remain unchanged. `Audio only` selected at every fresh launch (do not persist last-selected mode).
- Video helper: `MP4 with sound · Video quality and size from Settings.`; primary action `Queue video`; destination `Video folder`.
- Queue row must always show URL, **Type**, **Status** as text (not color alone). Use compact cards rather than a horizontally scrolling Windows table. Status vocabulary: Waiting, Downloading, Completed, Failed, Cancelled, Skipped; blocked folder shown distinctly with an action.
- Log is collapsible but always reachable from main screen; capped to recent 60,000 characters in UI; Copy log action. No decorative thumbnails, giant hero art, or full media player.
- Settings navigation: General / Audio / Video / Metadata / Appearance as five clearly labeled sections (scrollable tabs or list-to-section navigation); retain desktop names. Save and Cancel semantics: edits apply only on Save; Cancel discards draft.
- Metadata preview: 11 checkboxes, sample **Here Comes the Sun — The Beatles — Abbey Road**, and dynamically updated preview values and unsupported-field labels. No fabricated album art requirement.
- Theme: Light default; Dark explicit toggle in Appearance, persisted. Use desktop palette as identity: dark background `#070F2B`, input surface `#1B1A55`, text `#F7F6FF`, secondary `#9290C3`, accent `#535C91`, cyan in logo. Respect Android accessibility contrast and system text scaling. Use desktop `assets/penrose-white-grey.svg` as adaptive app icon source, not a generic download icon.
- Use native Compose touch targets ≥48dp, responsive layout, accessible labels, screen reader status announcements, keyboard/paste support. Do not add bottom navigation merely to fill space.

## 4. General settings and destinations

- Separate user-chosen **Audio folder** and **Video folder** (may be same); use `ACTION_OPEN_DOCUMENT_TREE` and persist URI permissions. Default unset; ask for the relevant folder only when queueing first job of that type, not at app launch or on mode switch.
- Display human-readable folder name/path. When unavailable/revoked/unwritable, pause FIFO at the affected job and show **Choose replacement…**; replacement updates saved destination and waiting jobs of the same type that reference the same old destination; preserve order. Cancelling picker keeps pause.
- Checkbox **Append source URL to audio and video filenames** (desktop calls it YouTube URL); off by default. For YouTube use canonical video URL; for other sources use canonical extracted source URL, sanitized and bounded. No unsafe path separators or traversal; cap title portion to ~110 UTF-8 bytes when suffix enabled.
- Save destination and setting snapshots at enqueue. Settings changes affect future queue items only.
- Size caps and encoding settings are global defaults captured per job; per-item overrides are out of scope.

## 5. URL handling and queue behavior

- Multiline input; accept whitespace-separated links; keep rejected input visible for correction and add a failed/rejected log entry. Mixed valid and invalid links must not block valid ones.
- **YouTube strict path:** watch/Shorts/youtu.be single video; strip playlist parameters; reject channels and playlist-only; no live streams; no cookies/login.
- **Extended yt-dlp path:** TikTok, Instagram, SoundCloud and other supported public URLs can be queued if yt-dlp extracts a single accessible non-live media item. Reject playlist/collection expansion and unsupported/private/auth-required media with clear error. Do not claim every site is guaranteed to work.
- Queue one job per canonical source identity **and mode** while pending/active; same source may be queued once as Audio and once as Video. Release reservation after completion, failure, cancel or clear. Source ID must be namespace-qualified (extractor + ID), avoiding collisions across websites.
- One worker, FIFO, no parallel downloads. Queue can accept either type while other type is processing. Cancel current terminates active extractor/FFmpeg/tagger tasks; Clear waiting cancels only queued items. A failed item does not stop later items.
- Persist queue state in Room. On app/process restart, pending jobs remain; interrupted active job becomes Waiting/Needs retry with no false Completed state. Resume partial staging when supported; ensure duplicate reservation rebuilds correctly.
- For screen-off/background work use an Android foreground service with user-visible progress notification and cancellation action; comply with current Android service types/notification permission rules. Avoid promising indefinite execution under battery restrictions.
- Share target (`ACTION_SEND`, `text/plain`) for browser/YouTube/TikTok links: prefill the same main-screen input and let user choose Audio/Video and Queue. Do not auto-download from a share intent.

## 6. Audio output and encoding — exact parity

Formats and UI labels:

| ID | Output | Encoding behavior |
|---|---|---|
| `best` | Best available (source format) | Keep source audio codec/container where possible |
| `aac` | AAC (AAC-in-M4A) | AAC/M4A |
| `alac` | ALAC (ALAC-in-M4A) | ALAC/M4A |
| `flac` | FLAC (.flac) | FLAC |
| `m4a` | M4A (AAC-in-M4A) | AAC/M4A |
| `mp3` | MP3 (.mp3) | LAME MP3 |
| `opus` | Opus (.opus) | Opus |
| `vorbis` | Vorbis (.ogg) | Vorbis/Ogg |
| `wav` | WAV (PCM, .wav) | PCM s16le |

- Quality modes: `default` (stream copy if compatible), `variable` (quality 0 highest to 10 lowest when encoder supports), `bitrate` (32–512 kbps where encoder supports). Defaults: best, default, variable quality 5, bitrate 192 kbps. Disable unsupported modes for format, as desktop does.
- Conversion never increases original source quality. Explicit quality/bitrate requests trigger re-encoding; default prefers compatible stream copy.
- Default output filename is cleaned **video title without video-ID suffix** (desktop audio behavior); collision does not overwrite existing output. Unicode preserved.
- Work in app-private staging and publish completed files to SAF destination only after validation and processing. Keep failed/cancelled partials for retry where feasible; cleanup successful staging.
- Validate resulting audio has a readable audio stream before publish. Metadata/tag errors are nonfatal warnings; unreadable audio is fatal.

## 7. Video output — mandatory parity, not optional

- Mode **Video + audio** outputs a single `.mp4` with actual **H.264 video AND AAC audio**. Audio-only, video-only and cover-art-only MP4 are invalid.
- Maximum height settings: **Best available (0), 2160p, 1440p, 1080p, 720p, 480p, 360p**. Treat selection as ceiling. Prefer compatible separate video/audio streams, fall back to compatible combined stream. If no compatible stream exists, fail with an explicit diagnostic rather than silently producing a different codec.
- Merge without transcoding when compatible and under size cap; FFmpeg may be used to mux. `ffprobe` or equivalent validation checks H.264 real video stream and AAC audio stream before publication.
- Video filename includes source video ID by default, matching desktop. Append sanitized canonical URL if enabled. Preserve no-overwrite behavior.
- Video branch **skips music tag catalogue enrichment and ReplayGain**; do not apply audio-only settings to video. Capture resolution, video folder and video size cap per queued item.
- Video file cap re-encodes oversized outputs to H.264/AAC with progressively reduced bitrates; must validate codec and actual size afterward.

## 8. Independent file-size caps — mandatory parity

- Settings Audio and Video each have separate enable checkbox and MB input. Default disabled (`0`); allowed 0–16384 MB, decimal **1 MB = 1,000,000 bytes**.
- Compare fully staged output to strict byte cap. If already **strictly below** cap, do not re-encode. If at/above cap, derive bitrate budget from probed duration, transcode and retry up to seven times with conservative bitrate reductions; fail when even minimum viable bitrate cannot fit. Never publish an oversized or partial file.
- Audio: for bitrate-capable lossy selected formats, use compatible format; for Best available or lossless requested output when cap forces encoding, use **AAC-in-M4A** (desktop behavior). Reapply/verify selected metadata and ReplayGain where applicable after final transcode; avoid writing tags that are subsequently discarded.
- Video: H.264/AAC, with actual stream and file-size verification after reduction. Cancellation must stop processing and leave destination untouched.
- Explain potential quality reduction and impossible caps in Settings.

## 9. Metadata: source-aware and configurable

- Fields, in this order: Title, Artist, Album, Album artist, Release date/year, Track number, Disc number, Genre, Composer, Source URL, Description/comment. All enabled by default.
- For YouTube music, use structured yt-dlp music metadata, then Topic description, then tags already in file, then a **confident Apple music catalogue match** for missing fields (Japanese/UK region selection, normalized title/artist and duration matching). Do not overwrite confidently known fields with catalogue guesses. Timeouts, rate limiting, cancellation and ambiguous matches must be nonfatal and logged.
- For TikTok and other sources, preserve only fields actually known. Uploader/creator is **not automatically song artist**, description is **not automatically song title**, thumbnail is **not automatically album artwork**. Do not fabricate album/track/genre. Avoid Apple lookup unless reliable song title/artist evidence exists.
- Checkbox OFF means omit/remove that tag in final output where format supports removal. Unsupported fields display **Unavailable** in sample preview and are not promised. MP4/M4A/AAC/ALAC: source URL unsupported; WAV: composer, source URL, album artist unsupported.
- After writing, read tags back and verify selected values and removal of deselected tags. If tag operation fails, warn and still publish a valid readable audio file. Log missing fields rather than blocking download.
- Metadata preview must use Beatles sample above, not dynamic network requests.

## 10. ReplayGain 2.0

- Settings → Audio checkbox **Add ReplayGain tags**, default ON; disabled for video. Desktop uses `rsgain` to scan **each track** and write per-track ReplayGain 2.0 gain and peak tags without changing samples. No album gain, no destructive loudness normalization.
- Android implementation must perform compatible loudness analysis and write format-appropriate gain/peak tags, then verify them; document codec/format support. If technically unavailable, leave UI visible with an accurate disabled status and record blocker; do **not** pretend normalization is ReplayGain.
- ReplayGain failures are warnings and must not discard otherwise valid audio. Run after any size-limit transcode so tags describe final audio.

## 11. Publication, cancellation and errors

- Staging is private to the app, keyed by sanitized source identity + kind; avoid path traversal and cross-job collisions. Use per-job lock. Never treat arbitrary extractor output path as trusted; require file under job staging directory and validate canonical path and extension.
- Publish by writing a temporary destination sibling where supported, verifying completed copy, then moving/renaming to final name without overwrite; for SAF providers lacking atomic move, use collision-safe create + stream copy and delete incomplete document on failure. Do not claim atomicity where provider cannot guarantee it.
- Existing destination name => Skipped, never overwritten. Distinguish network, unsupported URL, login-required, tool missing, invalid output, unwritable folder, size impossible, cancelled and metadata warning.
- Cancel current must terminate child jobs and avoid destination partials. Closing app with active jobs should not silently lose queue; service may continue or offer explicit cancel. Persist concise diagnostics.
- No hidden retry storms. A single explicit Retry action for failed jobs is acceptable; no parallel retry engine.

## 12. Settings defaults and validation contract

| Setting | Default | Values |
|---|---|---|
| Audio folder | unset | persisted SAF tree URI |
| Video folder | unset | persisted SAF tree URI |
| Theme | light | light / dark |
| Audio format | best | 9 options in §6 |
| Quality mode | default | default / variable / bitrate where supported |
| Variable quality | 5 | integer 0–10 |
| Bitrate | 192 kbps | 32–512 kbps |
| Audio max size | off | 0–16384 decimal MB |
| Video max size | off | 0–16384 decimal MB |
| Video max height | Best available | 0 / 360 / 480 / 720 / 1080 / 1440 / 2160 |
| Append URL to filename | false | boolean |
| 11 metadata fields | true | boolean each |
| ReplayGain | true | boolean, audio only |

Normalize invalid persisted values to defaults, and log settings corruption without crashing. Settings Save validates changed destinations and updates UI immediately; Cancel discards edits. Keep settings distinct from per-job snapshots.

## 13. Scope and explicit differences from Windows

- **Parity:** audio and video modes, both folders, FIFO queue, queue controls, diagnostics, formats, quality, independent size caps, metadata enrichment, ReplayGain, appearance, icon, filename policy, safe publication and recovery.
- **Android extension:** TikTok/other public yt-dlp single-item links, share sheet, persistent queue, foreground background downloads, native SAF folder picker.
- **Not parity / excluded:** desktop `.exe`, PowerShell setup, Windows Explorer metadata behavior, Windows high-contrast native rendering, live recording, playlist batches, cookie/login access, downloading DRM-protected media, music playback, library management, sync and cloud server.
- Source websites may change; show extractor failure and allow updating dependencies through app releases, not silent remote executable downloads.

## 14. Tests and acceptance gates

Automated unit tests for URL normalization, strict YouTube parsing, source ID namespacing, duplicate reservation, queue snapshots, settings normalization, encoding option availability, filename sanitization, Unicode, size-cap budgeting, tag selection and metadata confidence matching. Integration/device tests for actual Android binaries and SAF.

**Must pass before declaring full parity:**

1. Audio A, Video A and Video B can be queued together; FIFO and type-specific duplicate rejection work.
2. Switching selected mode mid-download does not change active/queued jobs or erase input.
3. First-run video can be configured without audio folder; cancel folder picker preserves input.
4. YouTube watch, Shorts, youtu.be accepted; playlist-only, channels and live streams rejected.
5. At least one accessible TikTok single video URL works in both modes or is documented as an extractor limitation; unsupported sites fail clearly.
6. All nine audio format options are tested with actual supported encoders or explicitly marked blocked with evidence; copy versus explicit encode semantics verified.
7. Video outputs playable H.264/AAC MP4; resolution ceilings and fallback respected; ffprobe validation rejects audio-only and video-only files.
8. Both caps: under cap untouched; over cap reduced; impossible cap fails without output; exact decimal-byte check.
9. Metadata: source → Topic → existing tags → confident Apple catalogue; no overwrite of known tags; unsupported tag labels and 11 checkboxes; selected tags verified and deselected removed.
10. ReplayGain 2.0 per-track gain/peak tags verified on final audio; no audio-sample normalization; warning on failure.
11. Destination collision never overwrites; cancellation leaves no partial published file; staged partials safely retained when possible.
12. Blocked folder pauses FIFO, replacement repairs matching waiting jobs only; Cancel keeps pause.
13. Theme persists; both palettes and Penrose icon render; labels usable with accessibility scaling.
14. Share link from Android browser/TikTok opens input without automatic download; foreground download continues under screen lock on a supported device (within Android limits).
15. GitHub Actions builds debug APK artifact; in-place APK update preserves DataStore settings and Room queue/history with same signing key.

Never claim integration tests passed on mock output. Record tested URLs generically, test date, device/API, tool versions and any site restrictions.

## 15. Fixed milestone sequence

**M1 — Android shell and parity UI.** Create Kotlin/Compose project, themes/icon, mode switch, URL input, queue/log placeholders, all five Settings sections, DataStore schema, Room schema, navigation, tests and GitHub Actions APK. No fake claim of functional downloads.

**M2 — YouTube extraction + FIFO + destinations.** Integrate yt-dlp and FFmpeg/ffprobe; strict YouTube parser; audio best-source download; video H.264/AAC MP4 download; two SAF folders; staged output; cancellation; actual status/log; background foreground service; duplicate reservation. Test real audio AND video before proceeding.

**M3 — Full audio formats and video controls.** Implement all nine formats, stream-copy/quality/bitrate modes, seven video resolution options, validation and filename rules. Verify actual codecs.

**M4 — Independent size caps and safe publishing.** Audio/video decimal-MB limits, bounded transcode retries, post-process verification, no-overwrite, collision handling, folder recovery and partial cleanup/resume.

**M5 — Full metadata and ReplayGain.** All 11 fields, sample preview, source/Topic/tag/Apple matching, selected-tag removal, read-back verification, per-track ReplayGain 2.0 tags and warning semantics.

**M6 — Multi-source and Android polish.** TikTok/other supported single-item sources, share intent, persistence/recovery hardening, accessibility, theme polish, update-safe signing, end-to-end device testing, README and release checklist.

At every milestone: `./gradlew test assembleDebug` (or equivalent), run relevant emulator/device tests where possible, publish `app-debug.apk` artifact, update `IMPLEMENTATION_STATUS.md`, and **stop**. Do not burn usage implementing future milestones early.

## 16. Codex repository deliverables

- Android Studio/Gradle project, reproducible dependency setup, README for build/install, `.github/workflows/android.yml`.
- `IMPLEMENTATION_STATUS.md` with milestone checklist, test results, blocked features, APK artifact location and next action.
- Source code and tests, no checked-in tool binaries unless licenses and repository policy permit them.
- `docs/DESKTOP_PARITY.md` mapping every feature in §1 to Android implementation and actual test evidence.

## 17. First Codex instruction (copy verbatim)

> Read `ANDROID_DOWNLOADER_SPEC.md` in full, particularly the audited desktop feature inventory and fixed UI in sections 1–3. Use https://github.com/dgx3-jpg/BareBonesDownloader as the desktop behavioral/visual reference. Implement **Milestone 1 only**. Do not redesign or reduce this to an audio-only app. Do not spend time proposing alternatives. Create the Android project, parity UI, persistent schemas, tests, and GitHub Actions debug APK artifact. Update `IMPLEMENTATION_STATUS.md` and `docs/DESKTOP_PARITY.md` with exactly what works and what is untested, then stop.
