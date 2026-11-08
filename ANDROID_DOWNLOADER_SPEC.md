# Android Media Downloader — Codex Implementation Specification

**Status:** Implementation specification, v1.0  
**Target:** Standalone, sideloadable Android APK  
**Primary objective:** Download accessible online media to locally stored audio files with predictable processing, metadata, and queue management.  
**Decision policy:** Implement this specification. Do not redesign product scope or ask routine design questions.

## 0. Instructions to Codex (read first)

1. Treat every **MUST** as a requirement, **SHOULD** as a strong preference, and **OUT OF SCOPE** as prohibited for v1.
2. Implement in the ordered milestones in §13. Work on one milestone at a time; run its tests and report results before moving on. Do not spend time proposing alternative frameworks or UI designs.
3. Make small engineering decisions without asking (class names, internal helpers, padding within specified scale). Ask only when blocked by missing credentials, unavailable toolchains, incompatible licenses, or an irreversible product decision not covered here.
4. Never claim that downloads, audio formats, metadata, ReplayGain or background operation work unless verified on a device/emulator or with an appropriate automated test. Clearly distinguish mocks from real integration.
5. Prefer maintained upstream libraries. Verify current versions, license obligations, ABI support and Android compatibility at implementation time; pin working versions. Do not blindly adopt an abandoned FFmpegKit binary.
6. If a site changes and yt-dlp extraction fails, surface a useful failure and allow an extractor update; do not implement custom scraping or DRM circumvention.
7. Keep a concise `IMPLEMENTATION_STATUS.md` with completed features, actual test evidence, blockers, and the next milestone. Avoid large speculative planning documents.
8. The user will build and sideload the APK for personal use. No Play Store submission or cloud backend is required.

## 1. Fixed product decisions

| Decision | Requirement |
|---|---|
| Application | Standalone native Android app; no Windows app connection or syncing |
| Language/UI | Kotlin; Jetpack Compose; Material 3 |
| Architecture | Single-activity; MVVM with repositories and clearly separated download/processing services |
| Android support | minSdk 26 (Android 8); compile/target latest stable SDK compatible with selected toolchain |
| Distribution | Debug/release APK for manual sideloading |
| Download engine | `yt-dlp` through a maintained Android integration (prefer `yausername/youtubedl-android` if compatible); do not reimplement extractors |
| Processing | Android-compatible FFmpeg executable/library, selected and pinned after ABI/license verification |
| Local persistence | Room for queue/history; DataStore Preferences for settings |
| Storage | User-selected directory using Storage Access Framework (persistable URI permission); do not require all-files access |
| Background work | Foreground service for user-initiated active downloads with visible notification; WorkManager only for suitable deferred/recovery work |
| Parallelism | One active download/processing job at a time; FIFO queue |
| Authentication | No app account; optional user-provided cookies support deferred beyond MVP |
| Theme | Material 3 dynamic colour where available, system light/dark mode |
| Language | English UI |
| Default mode | Audio-only |
| Network | Wi-Fi or mobile data allowed; no built-in network restrictions in v1 |

### Scope

**MUST:** paste/share URL, inspect source, queue items, extract best available audio, convert to user-selected format, apply supported metadata, optionally apply ReplayGain, save to chosen folder, handle failures, show progress, persist queue/history, download in background, build APK.

**SHOULD:** support YouTube, YouTube Music, TikTok, SoundCloud and other URLs supported by installed yt-dlp extractors. Availability depends on site restrictions; never guarantee every link.

**OUT OF SCOPE v1:** streaming player, music-library management, account login, cloud sync, PC integration, automatic music identification/fingerprinting, AI-generated metadata, video editing, batch playlist/channel crawling, scheduled downloads, automatic updates from untrusted sources, DRM bypass, circumventing paywalls, Play Store publishing, and automatic TikTok song identification.

## 2. User journeys

### A. Paste a link
1. Open Home screen.
2. Paste URL into single-line field; Paste action reads clipboard only on explicit tap.
3. Tap **Add to queue**. Validate http/https URL; reject malformed/unsupported schemes.
4. Run metadata inspection asynchronously. Show title/uploader/thumbnail if available, otherwise source hostname and generic title.
5. Insert queue item, then start automatically if idle.
6. Display progress and current stage: Inspecting → Downloading → Processing → Tagging → Saving → Completed.
7. Save file to configured directory; expose **Open file** and **Share file** on completion.

### B. Android Share
1. Register `ACTION_SEND` with `text/plain` for a shared URL.
2. Extract a valid URL from shared text, show the Home screen with the URL populated and automatically add it to queue once (no duplicate intent processing on recreation).
3. If shared text has no valid URL, show a clear message and do not queue anything.
4. Support cold start and already-running app.

### C. Queue
1. Queue persists across app restarts.
2. One item processes at a time in insertion order.
3. Allow **Cancel** active item, **Remove** queued item, **Retry** failed item, **Clear completed**.
4. A failed item does not stop subsequent queued items.
5. Cancellation stops yt-dlp/FFmpeg processes, cleans temporary files and leaves already completed files intact.
6. On process death/reboot, mark interrupted work `Paused — retry required` and allow explicit retry; do not silently duplicate downloads.

## 3. UI specification — no design exploration

### Navigation
Bottom navigation with exactly **Home**, **Queue**, **History**, **Settings**. Single-activity Compose navigation. Do not add onboarding carousel, dashboard, analytics or discovery tabs.

### Home
- App bar: `Downloader`; settings icon optional (bottom navigation already includes Settings).
- One outlined URL field labelled `Media URL`, placeholder `Paste a YouTube, TikTok or other link`.
- Trailing Paste icon button; below field a full-width primary `Add to queue` button.
- Small line: `Supports sites available through yt-dlp; some links may require access or may stop working.`
- Below: latest active download card with thumbnail (if available), title, stage, determinate progress if reported, otherwise indeterminate indicator.
- No search, recommendations or built-in browser.

### Queue
- Vertical list: thumbnail 56dp, title (two lines), source, status, progress bar, contextual actions.
- Active item at top; remaining queued FIFO. Failed items retain error summary and Retry.
- Show empty state `No downloads queued`.

### History
- Most recent completed/failed items first; show title, date, format, status and filename.
- Completed: Open, Share, Remove from history (never delete media implicitly).
- Failed: error summary, Retry, Remove from history.
- Provide Clear history with confirmation; this deletes database history only, not files.

### Settings
Sections and exact controls:

**Downloads**
- `Save folder`: SAF folder picker; default requires user selection before first download (show blocking folder-picker prompt when needed).
- `Output type`: Audio only (default); video downloading out of scope v1.
- `Audio format`: Original audio (default), MP3, M4A/AAC, Opus, FLAC, WAV. Show that FLAC/WAV do not improve lossy source quality.
- `Audio quality`: Best available (default), High, Medium, Low. Mapping specified in §5.
- `Filename template`: fixed default `{artist} - {title}.{ext}` when artist/title reliable, otherwise `{source} - {title_or_id}.{ext}`. No custom template editor in v1.

**Metadata**
- Switches: Title ON; Artist ON; Album ON; Album artist ON; Track number ON; Release date ON; Genre ON; Artwork ON; Source URL OFF; Uploader OFF; Description OFF.
- `Preview tags` opens a static example based on `Come Together — The Beatles` and displays values for enabled fields. Explicitly label example as illustrative; never pretend a sample link was fetched.
- Missing fields remain empty, not invented; no hard failure because metadata is absent.

**Audio processing**
- `ReplayGain`: OFF by default.
- `ReplayGain target`: fixed -18 LUFS integrated loudness analysis target for v1 (informational only; see §7). Do not alter audio samples when tagging.
- Show explanation: `Writes compatible loudness metadata where supported; playback depends on your music player.`

**About**
- App version; yt-dlp version; FFmpeg version/license; open-source licenses; `Check extractor updates` button (manual and safe update mechanism as supported by chosen integration).

### Visual design tokens
- Material 3 default typography and components; system font.
- Screen horizontal padding 16dp; vertical section spacing 16dp; card radius 12dp; touch targets >=48dp.
- Dynamic colour on Android 12+; system palette fallback below.
- Avoid gradients, custom icon packs, animated illustrations, fake waveform graphics and complicated gestures.
- Accessibility: semantic labels, TalkBack, scalable text, no colour-only status indicator.

## 4. Data and state model

Room entity `DownloadItem`:
- `id: UUID` (primary key)
- `sourceUrl: String`
- `sourceHost: String?`
- `extractorKey: String?`
- `mediaId: String?`
- `title: String?`
- `uploader: String?`
- `artist: String?`
- `album: String?`
- `thumbnailUrl: String?`
- `status: enum` (`QUEUED`, `INSPECTING`, `DOWNLOADING`, `PROCESSING`, `TAGGING`, `SAVING`, `COMPLETED`, `FAILED`, `CANCELLED`, `INTERRUPTED`)
- `progressPercent: Int?` (null when unknown)
- `outputFormat: enum` snapshot at enqueue
- `outputUri: String?`
- `errorCode: String?`; `errorMessage: String?`
- `createdAt`, `updatedAt`, `completedAt` timestamps
- `metadataSettingsSnapshot` and `replayGainEnabledSnapshot` (JSON or typed embedded data)

**Rules:** Settings are captured per item at enqueue; changing settings does not mutate queued jobs. Database operations must be transactional. Keep UI state flows reactive. Do not store huge media blobs or thumbnails in Room.

## 5. Download and format rules

1. Validate URL scheme and use yt-dlp extractor to inspect. Avoid a hardcoded list of accepted hostnames.
2. Prefer best available audio-only stream; if unavailable, download best accessible media stream and extract its audio.
3. **Original audio:** preserve source audio codec/container when feasible without transcoding; if demux/remux is necessary, use stream copy. Output extension must reflect actual container.
4. **MP3:** encode with `libmp3lame` VBR q=2 (high), q=4 (medium), q=6 (low); best available uses q=2. If encoder unavailable, fail with actionable error rather than silently using a different codec.
5. **M4A/AAC:** use AAC encoder at 256k (high/best), 192k (medium), 128k (low), with `.m4a` output.
6. **Opus:** use libopus at 160k (high/best), 112k (medium), 80k (low), `.opus` output.
7. **FLAC:** lossless encode from decoded source (no quality selector; display `Source-limited`).
8. **WAV:** PCM 16-bit stereo where appropriate; preserve source sample rate unless unsupported. No quality selector.
9. Do not claim a higher output bitrate means improved source quality. Avoid re-encoding for Original mode.
10. Use a private temporary working directory; never save partial files directly to final public folder. Final file copy through SAF must be atomic where possible; on failure remove incomplete destination document.
11. Sanitize filenames (control characters, separators, reserved names, whitespace); cap basename length; collisions get ` (2)`, ` (3)` suffix. Never overwrite existing files silently.
12. Use structured subprocess argument arrays, not shell-string concatenation; treat titles/URLs as untrusted data.
13. yt-dlp may return a site-specific or temporary error. Preserve a short actionable message and full diagnostic in internal logs (without sensitive cookies).

## 6. Metadata rules

### Source-aware interpretation
- For music-oriented extractors, map explicit music fields when present: track/title, artist, album, album artist, release date, track number, genre, thumbnail.
- For TikTok, Instagram, Reddit and generic video sites, **never** infer that uploader = music artist or caption = song title. Use a sanitized video title/description only as generic title when appropriate; artist/album blank unless explicitly supplied as music fields.
- For YouTube generic videos, uploader may be a channel, not a musician; keep uploader separate from artist.
- Never use network search, AI, MusicBrainz, Spotify or acoustic fingerprinting in v1 to guess missing tags.
- Download artwork from extractor thumbnail only when available and permitted; embed where supported. Missing/broken artwork must not fail download.

### Tag mapping
- MP3: ID3v2.3/2.4 compatible standard fields, APIC artwork.
- M4A: MP4 atoms, `covr` artwork.
- FLAC/Opus: Vorbis comments; picture block/format-appropriate embedded image where supported.
- WAV: only reliable supported tags; warn in UI that artwork/metadata compatibility varies.
- Original audio: best-effort tagging only where container supports it; no forced transcode just to add tags.
- Source URL/uploader/description: use supported comment/custom fields; do not overwrite artist/title.
- Use an Android-compatible tagging library or FFmpeg metadata muxing, whichever tests demonstrate works for target formats; do not assume one library supports every format.
- If tagging fails, retain audio, record `Completed with metadata warning` in history and log the warning. Do not delete the downloaded audio.

### Example preview (static)
Title `Come Together`; Artist `The Beatles`; Album `Abbey Road`; Album artist `The Beatles`; Release date `1969`; Track `1`; Genre blank; Artwork illustrative placeholder; Source URL blank; Uploader blank; Description blank. Toggling a field removes it from preview.

## 7. ReplayGain: precise v1 contract

- ReplayGain is **tag-only**; do not apply irreversible loudness normalization to the samples.
- Analyze completed audio using FFmpeg `ebur128` or equivalent integrated LUFS and true peak measurement. Compute gain as `targetLUFS - measuredLUFS`.
- Target is -18 LUFS; note this is an application-defined reference, not a claim of exact ReplayGain 2 reference compliance.
- Write track gain and peak in established format-specific ReplayGain tags only where supported and verified by tests; do not invent unsupported tags.
- No album-gain analysis in v1; no album grouping.
- If selected format cannot store useful ReplayGain tags, finish download with a warning, not a false success claim.
- Expose analysis result in diagnostics (LUFS, gain dB, peak) and test against known audio fixtures.
- ReplayGain only has audible effect if the playback app reads and applies the tags.

## 8. Background execution, permissions, and storage

- Start a foreground service for an explicit user-initiated download; implement Android-version-specific foreground-service types and permissions based on current Android SDK requirements.
- Show ongoing notification with title, progress/stage and Cancel action. Use notification permission where applicable; handle denial gracefully.
- Use cancellation-safe coroutines/process lifecycle; terminate child processes and delete temp artifacts.
- Keep screen-off and app-background downloads functional while the OS permits foreground execution. Do not promise immunity from force-stop, battery optimization, or OEM restrictions.
- Use SAF `ACTION_OPEN_DOCUMENT_TREE` and persist read/write permission; test invalid/revoked permissions and SD-card folders.
- No `MANAGE_EXTERNAL_STORAGE`, root, accessibility-service scraping or unrestricted file-system scanning.
- Do not log tokens, cookie contents or private shared URLs unnecessarily.
- No analytics, telemetry, ads or tracking SDKs.

## 9. Errors and recovery

User-facing error categories:
- Invalid URL
- Unsupported extractor / inaccessible page
- Authentication or region restriction
- Network interruption / timeout
- Missing or incompatible FFmpeg encoder
- Insufficient storage
- Folder permission revoked
- Media conversion failure
- Metadata warning (audio retained)
- ReplayGain warning (audio retained)
- Cancelled / interrupted

Use concise plain-English messages, with `Details` for technical diagnostics. Retry network/extractor failures manually in v1; no automatic endless retries. If inspection fails, keep failed entry in history. Do not suppress exceptions or report a completed file before verifying it exists and has nonzero size.

## 10. Security, rights, and dependency hygiene

- Only download content users have rights or permission to save; show this notice in About. Respect site restrictions and applicable terms.
- Do not implement DRM removal, anti-bot evasion, credential harvesting or proprietary client impersonation.
- Keep dependencies pinned with reproducible Gradle configuration; document licenses for yt-dlp integration, FFmpeg build and tagging libraries.
- Avoid loading arbitrary binaries from untrusted download links. For extractor updates, use the integration's verified update mechanism or app update if safe in-app updates are unavailable.
- Validate MIME/file extension and file existence before sharing through Android content URIs; never expose `file://` URIs.

## 11. Repository structure (recommended exact layout)

```text
android-downloader/
  README.md
  ANDROID_DOWNLOADER_SPEC.md
  IMPLEMENTATION_STATUS.md
  app/
    src/main/java/.../
      MainActivity.kt
      ui/{home,queue,history,settings,components}/
      data/{db,settings,repository}/
      domain/{model,usecase}/
      download/{YtDlpGateway,DownloadCoordinator,DownloadForegroundService}/
      processing/{AudioConverter,MetadataWriter,ReplayGainAnalyzer}/
      storage/{SafStorage,FilenamePolicy}/
      share/ShareIntentHandler.kt
    src/test/
    src/androidTest/
  gradle/
```

Interfaces must isolate yt-dlp, FFmpeg, storage and metadata so tests can use fakes. Avoid a monolithic Activity or a single massive DownloadManager class.

## 12. Acceptance test matrix

| ID | Test | Pass criterion |
|---|---|---|
| A01 | Paste valid public URL | Item inspected, queued, downloaded and saved |
| A02 | Share URL from another app | URL queued once; works on cold/warm start |
| A03 | Invalid text | Helpful validation; no crash |
| A04 | TikTok public video | Audio saved if yt-dlp extractor can access source; missing artist does not block |
| A05 | YouTube music item | Available track metadata mapped without inventing missing values |
| A06 | MP3/M4A/Opus/FLAC/WAV | Output decodes; extension/container match |
| A07 | Original audio | No unnecessary lossy re-encode |
| A08 | No metadata available | Download succeeds; safe filename; empty tags |
| A09 | Artwork unavailable | Audio succeeds; warning only if useful |
| A10 | ReplayGain on | Supported output has validated track gain/peak tags; no sample normalization |
| A11 | ReplayGain off | No analysis/tagging work performed |
| A12 | Queue 3 URLs | FIFO, one active at a time |
| A13 | Cancel active job | Processes stop; partial files cleaned; next job starts |
| A14 | Kill app process | No duplicate output; interrupted status recoverable |
| A15 | Background/locked screen | Foreground notification; continues where Android permits |
| A16 | Revoke folder permission | Actionable error; no silent alternate folder |
| A17 | Duplicate filename | Unique suffix; existing file unchanged |
| A18 | Clear history | Existing media remains untouched |
| A19 | Change settings mid-queue | Existing jobs retain enqueue-time settings |
| A20 | Offline/no network | Actionable error; app remains responsive |
| A21 | Accessibility | TalkBack labels, scalable type, usable 48dp controls |
| A22 | Build | Reproducible debug APK; documented release signing steps |

Test with owned/public-domain media where practical. Use local fixture files for format, metadata, loudness and filename tests; do not make CI depend on fragile third-party live URLs. Mark live extractor tests as manual/integration checks.

## 13. Fixed implementation milestones

### Milestone 1 — Scaffold and UI shell
- Create Android Studio Gradle project, Compose navigation and all four screens.
- Define entities, repositories, settings defaults, preview tag example and UI components.
- Use fake download data only at this stage; label it as fake in implementation status.
- **Done when:** project compiles, navigation works, settings persist, basic UI tests pass.

### Milestone 2 — Real extraction and file saving
- Integrate pinned yt-dlp Android engine and supported FFmpeg build.
- Implement URL inspection, one real audio download, Original mode and SAF saving.
- **Done when:** public test URL yields playable file in chosen folder on physical device/emulator; log actual versions.

### Milestone 3 — Queue and background service
- Implement Room-backed FIFO coordinator, progress, cancellation, foreground notification, interrupted recovery.
- **Done when:** A12–A16 pass on a real device.

### Milestone 4 — Formats and metadata
- Implement all requested audio formats and quality presets; source-aware tag mapping, artwork, filename collisions, metadata preview.
- **Done when:** A05–A09 and A17/A19 pass with media inspection tools.

### Milestone 5 — ReplayGain
- Add track loudness analysis and format-appropriate tags, warnings for unsupported cases.
- **Done when:** A10–A11 pass against measured audio fixtures.

### Milestone 6 — Share, history and release readiness
- Implement Android share targets, history actions, error diagnostics, accessibility, release build documentation.
- **Done when:** complete matrix reviewed, APK installs and works on an actual phone.

## 14. Required Codex deliverables after each milestone

Provide:
1. Files changed (short summary).
2. Features actually working (not merely coded).
3. Exact build/test commands run and outcomes.
4. Device/emulator verification performed or explicitly not performed.
5. Remaining blockers and next milestone.
6. Updated `IMPLEMENTATION_STATUS.md`.

Do not produce repeated long planning prose. Do not move to the next milestone if build is broken. When a dependency is blocked, document the smallest compatible substitution and its trade-off before implementing it.

## 15. First Codex instruction

> Read `ANDROID_DOWNLOADER_SPEC.md` in full. Implement **Milestone 1 only**. Follow the fixed architecture, UI, defaults and scope exactly. Make routine engineering decisions without asking me; do not debate alternative stacks or add features. Create and maintain `IMPLEMENTATION_STATUS.md`. Run available builds/tests and report precisely what passed and what could not be verified. Stop after Milestone 1 and await my instruction to continue.
