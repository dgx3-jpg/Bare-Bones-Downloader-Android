# Desktop parity — milestones 1–3, design v2

Reference: `dgx3-jpg/BareBonesDownloader`, audited in design v2. No desktop code was modified. This map describes implemented counterparts; runtime evidence and limits are in [implementation status](../IMPLEMENTATION_STATUS.md).

| Desktop behavior | Android counterpart | Verification / remaining scope |
| --- | --- | --- |
| DownloaderForm / YoutubeUrl | Compose main screen, strict canonical single-item YouTube parser, mixed-input retention, persistent FIFO and duplicate reservations | JVM/UI/Room tests; actual native and SAF tests. Live YouTube authentication blocked CI with both old and updated extractors. |
| SettingsForm / SettingsStore | Five draft sections, Save/Cancel, DataStore normalization, immutable per-job snapshots, independent SAF folders | Persistence, Room migration, UI tests and real DocumentsUI grant/read-back |
| AudioFormats / AudioEncoding | All nine formats, default compatible copy, explicit quality/bitrate encoding with actual encoder checks | Native owned-media encode/decode matrix; results in status. No quality improvement claim. |
| VideoOutput | H.264/AAC MP4 selectors, all seven height ceilings, actual codec/container/height validation | Owned H.264/AAC fixture and height rejection; live extraction remains separate |
| DownloadFiles / ProcessJob | Private staging, one foreground worker, cancellation of process descendants, verified SAF copy, collision skip, interrupted recovery | Native cancellation and real SAF/collision/cleanup passed; OEM/physical-device coverage remains manual |
| MediaSizeLimiter | Independent preferences, nonzero selected-type caps rejected explicitly | Actual cap processing is M4 |
| MusicMetadata / OnlineMetadata / ReplayGain | Eleven preferences and static format-aware sample | Actual enrichment/tag processing and ReplayGain are M5; completion warns that source tags may remain |
| AppTheme / Penrose identity | Explicit light/dark, SVG-derived adaptive icon | Persistence/UI/resource checks; physical launcher/accessibility review remains manual |
| Android packaging | Stable package, minSdk29, APK/report workflow, cached debug identity | Local and CI build checks; durable signing and broader hardening M6 |

Other sources, share intent and full release hardening remain M6. No full desktop parity claim is made.
