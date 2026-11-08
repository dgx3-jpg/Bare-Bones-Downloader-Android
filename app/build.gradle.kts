import java.net.URI
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.barebonesdownloader.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.barebonesdownloader.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "0.5.0"
        buildConfigField("String", "EXTRACTOR_SHA256", "\"e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    kapt("androidx.room:room-compiler:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Keep Robolectric's download lock/cache in the build tree, including in restricted CI.
tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    maxHeapSize = "1536m"
    doFirst { testHome.mkdirs() }
}

// Official, immutable extractor release. Downloaded only while building, never by the app.
val extractorResources = layout.buildDirectory.dir("generated/pinned-extractor-res")
val extractorFile = extractorResources.map { it.file("raw/ytdlp") }
val extractorDigest = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
val preparePinnedExtractor by tasks.registering {
    inputs.property("version", "2026.10.26")
    inputs.property("sha256", extractorDigest)
    outputs.file(extractorFile)
    doLast {
        val target = extractorFile.get().asFile
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (!target.exists() || digest(target) != extractorDigest) {
            target.parentFile.mkdirs()
            val partial = File(target.parentFile, "ytdlp.download")
            try {
                val connection = URI("https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp").toURL().openConnection()
                connection.connectTimeout = 30_000; connection.readTimeout = 60_000
                connection.getInputStream().use { input -> partial.outputStream().use(input::copyTo) }
                check(digest(partial) == extractorDigest) { "The pinned extractor checksum did not match. No binary was bundled." }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { partial.delete() }
        }
    }
}
android.sourceSets.getByName("main").res.srcDir(extractorResources)
tasks.named("preBuild").configure { dependsOn(preparePinnedExtractor) }

// Pure-Python tag writer, fixed release and digest, bundled at build time only.
val tagResources = layout.buildDirectory.dir("generated/tag-res")
val mutagenResource = tagResources.map { it.file("raw/mutagen") }
val prepareTagWriter by tasks.registering {
    inputs.property("sha256", "edd96f50c5907a9539d8e5bba7245f62c9f520aef333d13392a79a4f70aca719")
    outputs.file(mutagenResource)
    doLast {
        val target = mutagenResource.get().asFile
        target.parentFile.mkdirs()
        val partial = File(target.parentFile, "mutagen.download")
        try {
            val connection = URI("https://files.pythonhosted.org/packages/b0/7a/620f945b96be1f6ee357d211d5bf74ab1b7fe72a9f1525aafbfe3aee6875/mutagen-1.47.0-py3-none-any.whl").toURL().openConnection()
            connection.connectTimeout = 30_000; connection.readTimeout = 60_000
            connection.getInputStream().use { input -> partial.outputStream().use(input::copyTo) }
            val hash = MessageDigest.getInstance("SHA-256").digest(partial.readBytes()).joinToString("") { "%02x".format(it) }
            check(hash == "edd96f50c5907a9539d8e5bba7245f62c9f520aef333d13392a79a4f70aca719") { "Tag writer checksum mismatch" }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { partial.delete() }
    }
}
android.sourceSets.getByName("main").res.srcDir(tagResources)
tasks.named("preBuild") { dependsOn(prepareTagWriter) }
