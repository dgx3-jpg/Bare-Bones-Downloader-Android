# Bare Bones Downloader for Android

Native Kotlin/Compose Android port of [Bare Bones Downloader](https://github.com/dgx3-jpg/BareBonesDownloader), following [design v2](ANDROID_DOWNLOADER_SPEC.md). Android 10+; package `com.barebonesdownloader.android`; current candidate **0.6.1 / code 9**.

Audio and video downloads use a single persistent FIFO queue, independent SAF destinations and captured per-job settings. Native yt-dlp, FFmpeg/ffprobe, nine audio formats, quality/bitrate modes, H.264/AAC MP4 resolution ceilings, independent strict decimal-MB caps, configurable music tags and per-track ReplayGain are implemented. Public single-item sources beyond YouTube and Android plain-text share input are included. Sharing only prefills input; choose Audio/Video and tap Queue.

**Verification limits:** live YouTube extraction was blocked by a sign-in/verification request even with the updated extractor. Other sites can also restrict extraction. No login, cookie imports, DRM circumvention, live recording or playlist batches are implemented. See [actual results and APK links](IMPLEMENTATION_STATUS.md); no full device/site parity is claimed from fixture tests.

A permanent replacement signing identity is now established and privately backed up. An older differently signed CI APK needs a **one-time clean install**, resetting app-private settings/history. The old private key cannot be recovered. Future builds must use the new pinned key; no permanent signing key is kept in GitHub caches.

## Download / install

[GitHub Actions](https://github.com/dgx3-jpg/Bare-Bones-Downloader-Android/actions/workflows/android.yml) publishes `app-debug-apk` and verification reports. Download the artifact ZIP, extract `app-debug.apk`, and install it. Allow installation from your browser/file manager when Android requests it. The universal APK contains four ABIs and is about 225 MB. No Windows tools or FFmpegKit are used.

Use APKs with the same signing certificate for in-place updates. Uninstalling clears app data and folder grants. CI checks the permanent public certificate and refuses incompatible signed release assets; [signing continuity](docs/RELEASE_CHECKLIST.md) explains the private backup requirement and cache limitations.

## Use

1. Choose Audio only or Video + audio. Paste public single-media links, one per line or separated by whitespace.
2. Select the relevant destination folder when first requested; audio and video folders can differ. Queue the links. Invalid, inaccessible or duplicate input remains available for correction.
3. Configure defaults in General / Audio / Video / Metadata / Appearance. Save applies to future jobs; Cancel discards edits. Switching mode preserves links and queued jobs.
4. Cancel current stops active native work; Clear waiting affects pending jobs. After an interruption, use Retry or Resume explicitly. Revoked folder access pauses FIFO; choose a replacement to repair matching waiting jobs.

Keep the foreground notification enabled for progress/cancellation. Android/OEM service and battery limits still apply. The app holds a bounded wake lock during active work; it cannot promise indefinite background execution.

## Formats / processing

- Best available preserves source audio when possible. AAC and M4A contain AAC; ALAC uses M4A; FLAC, MP3, Opus, Vorbis and PCM16 WAV use their matching containers/codecs. Default copies compatible streams; explicit quality/bitrate requests encode. MP3 caps at 320 kbps; quality 10 maps to LAME 9. Unsupported combinations fail clearly.
- Video requires real H.264 video plus AAC sound in MP4. Seven height options are ceilings, with no upscaling. Generic sites may lack codec metadata until download; actual streams are checked before any cap transcode or publication.
- Both file caps use decimal MB and require the final tagged file to be **strictly smaller** than the cap. Under-cap output avoids re-encoding. Reduction is bounded to seven attempts; impossible caps publish nothing. Best/lossless audio becomes AAC-in-M4A when reduction is needed.
- Music fields resolve from explicit source credits, YouTube Topic descriptions, existing tags and confident Apple catalogue matches. Only missing fields are filled. Creators/uploaders are not assumed to be song artists. Unknown fields remain empty. Selected values and deselected-tag removal are read back and verified on private copies.
- Per-track ReplayGain uses EBU R128 loudness and sample peak analysis after encoding; tags do not normalize samples. Opus also gets its standard R128 gain. Metadata/ReplayGain failures warn and retain otherwise readable audio. Video skips music enrichment and ReplayGain.
- Publication verifies size and SHA-256 read-back, skips collisions and removes incomplete documents on handled failures. A private publication journal supports cleanup after process death. SAF providers offer no universal atomic/exclusive rename, and permission loss can require manual cleanup. No all-files permission.

## Permanent signing / CI

The permanent certificate fingerprint is in `tools/signing-certificate.sha256`. Keep the supplied private signing backup outside the repository. For a source-bound release from a clean checkout:

```sh
export BBD_DEBUG_KEYSTORE_PATH=/private/path/bbd-android.keystore
python3 tools/build-release.py
```

This builds the actual app plus signed instrumentation/update fixtures, verifies hashes/source commit/certificate and writes `release-build/`. The private key is never included. Release assets are `app-debug.apk`, `release-manifest.json`, and `update-verification-apks.zip`. A manual Actions run with `release_tag` downloads and verifies those public binaries, publishes the installable APK artifact and tests the actual permanent-key APK upgrade. No private signing key needs to enter CI.

Push/PR builds use temporary test keys when no signing secret is configured; temporary-key APKs are not published as installable releases. For optional fully automatic CI signing, the repository owner can configure Actions secret `BBD_DEBUG_KEYSTORE_BASE64` from the private backup. The connected app cannot administer that secret; offline signing works without it.

## Build / test

JDK 17 or 21; SDK Platform 36; Build Tools 35.0.0. Set `ANDROID_HOME` or an untracked `local.properties` with `sdk.dir=/your/sdk/path`.

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows: `gradlew.bat`. Gradle, AGP, Kotlin and dependencies are pinned. Official yt-dlp 2026.10.26 and Mutagen 1.47.0 are downloaded and checksum verified **at build time**, then bundled. The app does not update executables remotely.

With a dedicated API 29+ emulator connected (integration tests reset test preferences and create fixture history):

```sh
# Real codecs, caps, tags/RG, SAF, cancellation, share, recovery and owned HTTP FIFO fixtures:
bash tools/run-native-tests.sh false false
# Optional public-site checks; restrictions may cause explicit failures:
bash tools/run-native-tests.sh true true
```

The manual Actions workflow verifies a versioned same-certificate APK update; a signed release run uses the actual permanent-key baseline/current/test APKs. Its reports distinguish skipped live checks from successful downloads. Owned generated media and local test HTTP fixtures do not prove TikTok/YouTube availability. No downloaded creator media is included in report artifacts.

## Licensing / release status

Native integration and Mutagen include GPL components. This is the owner's private personal-use build. Wider redistribution requires compatible licensing and complete corresponding source/build materials; no new license over the owner's original code is inferred. Licenses are available in Settings → General → Open-source notices. See [third-party notices](THIRD_PARTY_NOTICES.md), [native provenance](docs/NATIVE_RUNTIME.md), [parity map](docs/DESKTOP_PARITY.md), and [release checklist](docs/RELEASE_CHECKLIST.md).
