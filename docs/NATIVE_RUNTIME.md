# Native runtime provenance — milestone 2 candidate

Pinned Maven Central coordinates: `io.github.junkfood02.youtubedl-android:{library,ffmpeg,common}:0.18.1`. Upstream repository/tag: https://github.com/yausername/youtubedl-android/tree/0.18.1. Central metadata lists this as the latest release at inspection on 2026-11-08.

Audited AAR SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| library | `579b5fb480892b1abc2b218c2089699d52759cc8d7ba256bf876453f0365faef` |
| ffmpeg | `0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49` |
| common | `e0c0530bdbdf203e1598e900085d3582e27360731872872f3a67f23a9b24c0b5` |

The library AAR bundles yt-dlp **2025.11.12** (`335653be82d5ef999cfc2879d005397402eebec1`), Python and QuickJS. The FFmpeg AAR supplies `libffmpeg.so`, `libffprobe.so` and packaged dependencies for **arm64-v8a, armeabi-v7a, x86_64, x86**. Native execution requires installer extraction (`useLegacyPackaging` / `extractNativeLibs`); no executable is downloaded at app runtime. Private staging and SAF avoid the obsolete broad-storage advice in the upstream README.

Archive inspection of the x86_64 libavutil configuration found `--enable-gpl --enable-version3 --enable-libmp3lame --enable-libopus --enable-libvorbis --enable-libx264 --disable-libfdk-aac`, and no `--enable-nonfree`. Configuration strings are **not** an executed codec test. Actual runtime versions, native decoding/encoding and process-group behavior must be verified by `NativeIntegrationTest` on Android before claiming support. Other ABIs and 16 KB page-size devices remain separate validation targets.

## Licenses and corresponding source

- Wrapper/common/FFmpeg integration: GPL-3.0; full upstream GPL text is bundled under `app/src/main/res/raw/native_gpl3.txt` and visible in Settings → General → Open-source notices.
- FFmpeg build enables GPL and version 3. FFmpeg/codec sources and dependencies are available through the upstream [FFmpeg build instructions](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md), [Termux source packages](https://github.com/termux/termux-packages), [FFmpeg](https://ffmpeg.org/download.html), x264, LAME, Opus and Vorbis projects.
- Python uses the PSF license; QuickJS uses MIT. Upstream runtime build instructions: [BUILD_PYTHON.md](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_PYTHON.md). yt-dlp's source/release license information: https://github.com/yt-dlp/yt-dlp/tree/335653be82d5ef999cfc2879d005397402eebec1.
- This is a private personal-use build made for the repository owner. Do not treat this document as a new license grant over the owner's original code. Redistributing a combined GPL-linked app requires GPL-compatible licensing and complete corresponding source/build materials, preserved notices and applicable dependency licenses. Wider redistribution and a complete source-distribution bundle are not asserted release-ready here.
- AndroidX, Kotlin and Apache dependencies retain their existing notices. No abandoned FFmpegKit binary is included.

## Execution and cancellation

Only initialization/unpacking is delegated to upstream. Its blocking `execute` and `destroyProcessById` are not used: the latter interpolates an ID into a shell command and does not provide the process-group guarantee needed here. `NativeRuntime` starts each trusted executable through a constant Python launcher with structured arguments, creates a fresh POSIX session and records its actual PID before exec. Cancellation kills that process group and drains output. URL/title text is never concatenated into a shell command. A real Android descendant-cancellation test must pass; host JVM fakes do not prove this behavior.

Extractor updates are through pinned app releases. No automatic, unsigned remote runtime update or custom extractor is implemented.
