# Desktop parity — milestones 1–6, design v2

Reference: `dgx3-jpg/BareBonesDownloader`, design v2's audited desktop tree. The implementation inspected `MediaSizeLimiter.cs`, `MusicMetadata.cs`, `OnlineMetadata.cs`, `DownloadFiles.cs`, `ProcessJob.cs` and the earlier UI/format references. No desktop code was modified. Actual evidence and remaining limits are in [implementation status](../IMPLEMENTATION_STATUS.md).

| Desktop behavior | Android implementation | Evidence / limits |
| --- | --- | --- |
| Main form / YouTube parser | Sparse Compose Audio/Video utility, strict watch/Shorts/youtu.be parser, input retention and status/log | JVM/UI tests; live YouTube sign-in restriction remains |
| FIFO / duplicate reservation | Room snapshots, namespace+ID+kind reservations, one foreground worker, restart rebuild and explicit Retry | Scheduling/recovery tests; owned HTTP end-to-end/native tests recorded in status |
| Settings / folders | Five Save/Cancel sections, DataStore defaults, independent SAF grants, blocked-folder replacement | Persistence/migration/UI and real DocumentsUI checks |
| AudioFormats / AudioEncoding | Nine formats, compatible default copy, explicit quality/bitrate encoding | Real Android codec/decode matrix; codec-specific limits are explicit |
| VideoOutput | H.264/AAC MP4, seven ceilings, fallback and actual stream/container/height validation | Native fixtures and height rejection; generic sites may have unknown codec metadata until inspected |
| MediaSizeLimiter | Decimal-byte strict caps, seven bounded reductions, lossy-selected/AAC fallback, final tag overhead checked | Real audio/video reduction and impossible-cap checks passed; final results in status |
| MusicMetadata / OnlineMetadata | Structured music → Topic → existing tags → confident Apple match; fill missing only; eleven controlled fields | Confidence/precedence tests; actual container write/removal/read-back; live catalogue matching not yet independently verified |
| ReplayGain | Per-track EBU R128/-18 LUFS gain and sample peak tags; Opus R128; no sample normalization | Actual gain/peak round-trip and encoded-audio hashes; silent/unsupported tracks warn |
| DownloadFiles / ProcessJob | Private UUID staging, POSIX-group kill, no-overwrite verified SAF copy, publication journal | Native cancellation/SAF checks; SAF and process-death transaction limits remain documented |
| Theme / Penrose identity | Explicit persisted light/dark, SVG-derived adaptive icon, scalable native labels/status semantics | Unit/UI/resource checks and scaled-theme screenshots where executed; physical launcher/TalkBack checks remain |
| Android extensions | Public single-item yt-dlp inspection, extractor-qualified IDs, plain-text share prefill, persistent diagnostics, foreground service | These are Android additions, not existing desktop features; TikTok/site restrictions reported separately |
| APK/update | Pinned build/runtime assets, artifact workflow, established certificate guard, optional private signing backup, real old/new update test | Actual run results in status; cache is not a durable key backup |

No full desktop/device/site parity claim is made while the documented acceptance limits remain.
