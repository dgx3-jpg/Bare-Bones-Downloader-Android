package com.barebonesdownloader.android

import android.content.Intent
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.*
import com.barebonesdownloader.android.media.YtDlpGateway
import com.barebonesdownloader.android.media.StagedMedia
import com.barebonesdownloader.android.storage.*
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/** Real Android runtimes and SAF. Live source: Blender Foundation Big Buck Bunny, CC BY 3.0; see native provenance. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NativeIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as DownloaderApplication
    @Test fun aNativeVersionsAndReadableAvFixture() = runBlocking {
        val runtime = app.runtime; val id = UUID.randomUUID()
        withContext(Dispatchers.IO) { runtime.initialize() }
        val versions = listOf(runtime.execute(id, runtime.python, listOf(runtime.ytdlp.path, "--version")).out,
            runtime.execute(id, runtime.ffmpeg, listOf("-version")).out,
            runtime.execute(id, runtime.ffprobe, listOf("-version")).out)
        versions.forEach { Log.i("BBD_INTEGRATION", it.take(6000)) }
        val encoders = runtime.execute(id, runtime.ffmpeg, listOf("-hide_banner", "-encoders")).out
        Log.i("BBD_INTEGRATION", encoders)
        val fixture = File(app.cacheDir, "owned-av-fixture.mp4")
        runtime.execute(id, runtime.ffmpeg, listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "testsrc=size=160x90:rate=10", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "1", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", fixture.path))
        val item = DownloadItem(id = id, sourceUrl = "fixture", kind = DownloadKind.VIDEO)
        YtDlpGateway(runtime, app.diagnostics).validate(item, fixture)
        assertTrue(fixture.length() > 0)
    }
    @Test fun eEveryAudioFormatAndExplicitEncodingModeDecodes() = runBlocking {
        val runtime = app.runtime
        withContext(Dispatchers.IO) { runtime.initialize() }
        val input = File(app.cacheDir, "owned-pcm.wav")
        runtime.execute(UUID.randomUUID(), runtime.ffmpeg, listOf("-v", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "sine=frequency=660:sample_rate=44100", "-t", "1", "-ac", "2", "-c:a", "pcm_s16le", input.path))
        val converter = com.barebonesdownloader.android.media.AudioConverter(runtime)
        val cases = AudioFormat.entries.map { AppSettings(audioFormat = it) } +
            AudioFormat.entries.filter { it.variable }.flatMap { format -> listOf(0, 10).map { AppSettings(audioFormat = format, qualityMode = QualityMode.VARIABLE, variableQuality = it) } } +
            AudioFormat.entries.filter { it.bitrate }.map { AppSettings(audioFormat = it, qualityMode = QualityMode.BITRATE, bitrateKbps = 160) }
        var aac: File? = null
        cases.forEachIndexed { index, settings ->
            val item = DownloadItem(sourceUrl = "owned-fixture", settingsSnapshot = settings)
            val output = converter.convert(item, input, File(app.cacheDir, "format-$index"))
            runtime.execute(item.id, runtime.ffmpeg, listOf("-v", "error", "-nostdin", "-i", output.path, "-f", "null", "-"))
            if (settings.audioFormat == AudioFormat.ORIGINAL) assertEquals(input, output)
            if (settings.audioFormat == AudioFormat.AAC && settings.qualityMode == QualityMode.DEFAULT) aac = output
            Log.i("BBD_INTEGRATION", "DECODED ${settings.audioFormat} ${settings.qualityMode} q=${settings.variableQuality} bytes=${output.length()}")
        }
        val compatible = DownloadItem(sourceUrl = "owned-fixture", settingsSnapshot = AppSettings(audioFormat = AudioFormat.AAC))
        assertEquals(aac, converter.convert(compatible, aac!!, File(app.cacheDir, "compatible-copy")))
        val explicit = compatible.copy(id = UUID.randomUUID(), settingsSnapshot = compatible.settingsSnapshot.copy(qualityMode = QualityMode.VARIABLE))
        assertNotEquals(aac, converter.convert(explicit, aac!!, File(app.cacheDir, "explicit-reencode")))
        val video = File(app.cacheDir, "owned-av-fixture.mp4")
        val gateway = YtDlpGateway(runtime, app.diagnostics)
        gateway.validate(DownloadItem(sourceUrl = "fixture", kind = DownloadKind.VIDEO, settingsSnapshot = AppSettings(videoMaxHeight = 360)), video)
        assertTrue(runCatching { gateway.validate(DownloadItem(sourceUrl = "fixture", kind = DownloadKind.VIDEO, settingsSnapshot = AppSettings(videoMaxHeight = 60)), video) }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun fStrictAudioAndVideoSizeCaps() = runBlocking {
        val runtime = app.runtime
        withContext(Dispatchers.IO) { runtime.initialize() }
        val limiter = com.barebonesdownloader.android.media.MediaSizeLimiter(runtime, app.diagnostics)
        val audio = File(app.cacheDir, "cap-input.wav")
        runtime.execute(UUID.randomUUID(), runtime.ffmpeg, listOf("-v", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "10", "-ac", "2", "-c:a", "pcm_s16le", audio.path))
        val item = DownloadItem(sourceUrl = "owned", settingsSnapshot = AppSettings(audioMaxSizeMb = 1))
        assertTrue(audio.length() >= 1_000_000)
        val reduced = limiter.apply(item, audio, File(app.cacheDir, "cap-audio"))
        assertTrue(reduced.length() in 1 until 1_000_000)
        assertEquals("m4a", reduced.extension)
        assertEquals(reduced, limiter.apply(item, reduced, File(app.cacheDir, "cap-untouched")))
        assertTrue(runCatching { limiter.applyBytes(item, audio, File(app.cacheDir, "cap-impossible"), 100) }.exceptionOrNull() is com.barebonesdownloader.android.media.SizeLimitImpossible)
        val video = File(app.cacheDir, "cap-input.mp4")
        runtime.execute(UUID.randomUUID(), runtime.ffmpeg, listOf("-v", "error", "-nostdin", "-y", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=20", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "8", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "0", "-c:a", "aac", video.path))
        assertTrue("Video fixture must exercise reduction", video.length() >= 1_000_000)
        val vitem = item.copy(id = UUID.randomUUID(), kind = DownloadKind.VIDEO, settingsSnapshot = AppSettings(videoMaxSizeMb = 1))
        val vreduced = limiter.apply(vitem, video, File(app.cacheDir, "cap-video"))
        assertTrue(vreduced.length() in 1 until 1_000_000)
        YtDlpGateway(runtime, app.diagnostics).validate(vitem, vreduced)
        listOf(reduced, vreduced).forEach { runtime.execute(UUID.randomUUID(), runtime.ffmpeg, listOf("-v", "error", "-nostdin", "-i", it.path, "-f", "null", "-")) }
        Log.i("BBD_INTEGRATION", "CAPS verified audio=${reduced.length()} video=${vreduced.length()} strict bytes; impossible failed.")
    }
    @Test fun bCancellationTerminatesNativeDescendant() = runBlocking {
        val runtime = app.runtime; val id = UUID.randomUUID(); val child = CompletableDeferred<Int>()
        val command = "import subprocess,sys,time; p=subprocess.Popen([sys.executable,'-c','import time;time.sleep(90)']); print('CHILD='+str(p.pid),flush=True); time.sleep(90)"
        val task = async(Dispatchers.IO) { runtime.execute(id, runtime.python, listOf("-c", command)) { line ->
            if (line.startsWith("CHILD=")) child.complete(line.substringAfter('=').toInt())
        } }
        val pid = withTimeout(20_000) { child.await() }
        runtime.cancel(id)
        assertTrue(runCatching { withTimeout(10_000) { task.await() } }.exceptionOrNull() is CancellationException)
        val end = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < end && runCatching { Os.kill(pid, 0) }.isSuccess) delay(100)
        assertTrue("Native child survived cancellation", runCatching { Os.kill(pid, 0) }.isFailure)
    }
    @Test fun cRealSafCopyCollisionAndCancellationWithOwnedMedia() = runBlocking {
        app.ready.await()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("mkdir -p /sdcard/Download/BBDM2Tests")
        app.settings.update { AppSettings() }
        ui.waitForIdle()
        ui.onNodeWithTag("open_settings").performClick()
        ui.onNodeWithTag("folder_AUDIO").performClick()
        // Real DocumentsUI grants, not fake persisted permission rows.
        val useFolder = By.text(Patterns.useFolder)
        if (!device.wait(Until.hasObject(useFolder), 10_000)) {
            device.findObject(By.desc("Show roots"))?.click()
            device.wait(Until.findObject(By.text("Internal storage")), 5_000)?.click()
        }
        device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)downloads?"))), 3_000)?.click()
        val directory = device.wait(Until.findObject(By.text("BBDM2Tests")), 5_000)
        if (directory == null) {
            val dump = File(app.cacheDir, "picker.xml"); device.dumpWindowHierarchy(dump)
            dump.readText().chunked(3000).forEach { Log.i("BBD_INTEGRATION", it) }
        }
        assertNotNull("The owned test directory was not visible in DocumentsUI", directory)
        directory.click()
        val use = device.wait(Until.findObject(useFolder), 10_000)
        assertNotNull("DocumentsUI did not expose a folder confirmation. ${device.currentPackageName}", use)
        use.click()
        device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)allow"))), 2_000)?.click()
        device.waitForIdle()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("BBDM2Tests").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("save_settings").performClick()
        val selected = withTimeout(10_000) { app.settings.settings.first { it.audioFolderUri != null } }
        val prefs = selected.copy(videoFolderUri = selected.audioFolderUri, videoFolderName = selected.audioFolderName)
        app.settings.update { prefs }
        val storage = SafMediaStorage(app)
        val video = File(app.cacheDir, "owned-av-fixture.mp4")
        assertTrue("Native fixture is missing", video.isFile)
        val audio = File(app.cacheDir, "owned-audio-fixture.m4a")
        app.runtime.execute(UUID.randomUUID(), app.runtime.ffmpeg, listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", video.path, "-map", "0:a:0", "-vn", "-c:a", "copy", audio.path))
        val saved = mutableListOf<String>()
        try {
            for ((kind, file) in listOf(DownloadKind.AUDIO to audio, DownloadKind.VIDEO to video)) {
                val item = DownloadItem(sourceUrl = "owned-fixture", mediaId = "owned", kind = kind, destinationUri = prefs.audioFolderUri)
                val media = StagedMedia(file, "Owned fixture ${kind.name}", if (kind == DownloadKind.VIDEO) "video/mp4" else "audio/mp4")
                val uri = storage.publish(item, media) {}; saved += uri
                val copy = File(app.cacheDir, "verified-${kind.name}.${file.extension}")
                app.contentResolver.openInputStream(android.net.Uri.parse(uri))!!.use { input -> copy.outputStream().use(input::copyTo) }
                assertArrayEquals(file.readBytes(), copy.readBytes())
                YtDlpGateway(app.runtime, app.diagnostics).validate(item, copy)
                assertTrue(runCatching { storage.publish(item, media) {} }.exceptionOrNull() is DestinationExists)
                var checks = 0
                val cancelled = media.copy(title = "Cancelled owned fixture ${kind.name}")
                assertTrue(runCatching { storage.publish(item, cancelled) { if (++checks >= 3) throw CancellationException("Test cancellation") } }.exceptionOrNull() is CancellationException)
                val tree = DocumentFile.fromTreeUri(app, android.net.Uri.parse(prefs.audioFolderUri))!!
                assertNull(tree.findFile(FilenamePolicy.name(item, cancelled.title, file.extension)))
                Log.i("BBD_INTEGRATION", "SAF saved and probed $kind; collision preserved original; cancelled destination removed.")
            }
        } finally { saved.forEach { DocumentFile.fromSingleUri(app, android.net.Uri.parse(it))?.delete() } }
    }
    @Test fun dLiveYoutubeAudioAndVideoReachSafAsReadableMedia() = runBlocking {
        Assume.assumeTrue("Live integration is explicitly opt-in", InstrumentationRegistry.getArguments().getString("live") == "true")
        app.ready.await()
        val prefs = app.settings.settings.first()
        assertNotNull("Real SAF permission test must run before live test", prefs.audioFolderUri)
        val source = InstrumentationRegistry.getArguments().getString("source") ?: "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        app.downloads.enqueue(source, DownloadKind.AUDIO, prefs)
        app.downloads.enqueue(source, DownloadKind.VIDEO, prefs)
        val ids = app.database.downloads().all().takeLast(2).map { it.id }.toSet()
        ui.runOnUiThread { ui.activity.startForegroundService(Intent(ui.activity, DownloadForegroundService::class.java)) }
        val results = withTimeout(600_000) { app.downloads.items.first { rows -> rows.filter { it.id in ids }.let { it.size == 2 && it.all { row -> row.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.SKIPPED, DownloadStatus.BLOCKED, DownloadStatus.INTERRUPTED) } } } }.filter { it.id in ids }
        Log.i("BBD_INTEGRATION", app.diagnostics.text.value)
        results.forEach { item ->
            assertEquals(item.errorMessage, DownloadStatus.COMPLETED, item.status)
            val file = File(app.cacheDir, "saved-${item.kind}.${if (item.kind == DownloadKind.VIDEO) "mp4" else item.outputFilename!!.substringAfterLast('.')}")
            app.contentResolver.openInputStream(android.net.Uri.parse(item.outputUri))!!.use { input -> file.outputStream().use(input::copyTo) }
            YtDlpGateway(app.runtime, app.diagnostics).validate(item, file)
            Log.i("BBD_INTEGRATION", "Saved and probed ${item.kind}: ${file.length()} bytes")
        }
    }
    private object Patterns { val useFolder: java.util.regex.Pattern = java.util.regex.Pattern.compile("(?i)(use this folder|allow access to.*|select)") }
}
