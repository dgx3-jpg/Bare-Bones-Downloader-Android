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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/** Real Android runtimes and SAF. The live source is yt-dlp's public test video. */
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
    @Test fun cLiveYoutubeAudioAndVideoReachSafAsReadableMedia() = runBlocking {
        Assume.assumeTrue("Live integration is explicitly opt-in", InstrumentationRegistry.getArguments().getString("live") == "true")
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
        val source = "https://www.youtube.com/watch?v=BaW_jenozKc"
        app.downloads.enqueue(source, DownloadKind.AUDIO, prefs)
        app.downloads.enqueue(source, DownloadKind.VIDEO, prefs)
        val ids = app.database.downloads().all().takeLast(2).map { it.id }.toSet()
        ui.runOnUiThread { ui.activity.startForegroundService(Intent(ui.activity, DownloadForegroundService::class.java)) }
        val results = withTimeout(300_000) { app.downloads.items.first { rows -> rows.filter { it.id in ids }.let { it.size == 2 && it.all { row -> row.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.SKIPPED, DownloadStatus.BLOCKED, DownloadStatus.INTERRUPTED) } } } }.filter { it.id in ids }
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
