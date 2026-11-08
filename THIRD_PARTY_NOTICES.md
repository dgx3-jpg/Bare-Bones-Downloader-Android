# Third-party notices — milestone 1

No yt-dlp, FFmpeg, tagging engine, media fixture, or native codec is bundled yet. Their versions, ABI support and license obligations must be assessed in milestone 2 before integration.

The Penrose icon is copied from the user’s desktop repository (`assets/penrose-white-grey.svg`, blob `997c55bfa1ac3e26f42229a2fdd0b09278169084`) and converted to Android vector/adaptive resources without redesign. Its original ownership is unchanged.

Runtime dependencies (including their AndroidX/Kotlin transitive modules):

| Dependency | Pinned version | License |
| --- | --- | --- |
| Kotlin standard library / Compose compiler | 2.2.21 | Apache-2.0 |
| Jetpack Compose / Material 3 | BOM 2025.10.01 (pins component versions) | Apache-2.0 |
| AndroidX Activity | 1.11.0 | Apache-2.0 |
| AndroidX Navigation | 2.9.5 | Apache-2.0 |
| AndroidX Lifecycle | 2.9.4 | Apache-2.0 |
| AndroidX DataStore Preferences | 1.1.7 | Apache-2.0 |
| AndroidX Room | 2.8.3 | Apache-2.0 |
| kotlinx.serialization JSON | 1.9.0 | Apache-2.0 |
| kotlinx.coroutines | AndroidX transitive dependency; resolved in Gradle dependency report | Apache-2.0 |

Build/test tools are not app runtime features: Gradle 8.13 and Android Gradle Plugin 8.13.2 (Apache-2.0), JUnit 4.13.2 (EPL-1.0), Robolectric 4.16.1 (MIT), AndroidX Test (Apache-2.0), kotlinx.coroutines-test 1.10.2 (Apache-2.0). Upstream artifacts retain their notices. The Gradle wrapper is generated from the official Gradle distribution.

The Apache-2.0 license is included in `app/src/main/res/raw/apache_license.txt` and can be viewed in Settings → Open-source licenses. Dependency sources and detailed license metadata are available from [AndroidX](https://android.googlesource.com/platform/frameworks/support/), [Kotlin](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), and [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization). No project-wide license grant is inferred for your original code.
