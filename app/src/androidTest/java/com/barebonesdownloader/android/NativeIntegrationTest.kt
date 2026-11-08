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
    @Test fun aNativeVersionsAndReadableAvFixture() = runBlocking<Unit> {
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
    @Test fun eEveryAudioFormatAndExplicitEncodingModeDecodes() = runBlocking<Unit> {
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
    @Test fun fStrictAudioAndVideoSizeCaps() = runBlocking<Unit> {
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
        Unit
    }
    @Test fun gMetadataAndReplayGainRoundTripWithoutChangingAudio() = runBlocking<Unit> {
        val runtime = app.runtime
        withContext(Dispatchers.IO) { runtime.initialize() }
        val writer = com.barebonesdownloader.android.metadata.AudioTags(runtime, app.diagnostics)
        val processor = com.barebonesdownloader.android.metadata.MetadataProcessor(runtime, app.diagnostics)
        val info = kotlinx.serialization.json.buildJsonObject {
            put("track", kotlinx.serialization.json.JsonPrimitive("音楽 & Song")); put("artist", kotlinx.serialization.json.JsonPrimitive("Known Artist"))
            put("album", kotlinx.serialization.json.JsonPrimitive("Known Album")); put("album_artist", kotlinx.serialization.json.JsonPrimitive("Album Artist"))
            put("release_date", kotlinx.serialization.json.JsonPrimitive("20200102")); put("track_number", kotlinx.serialization.json.JsonPrimitive("7"))
            put("disc_number", kotlinx.serialization.json.JsonPrimitive("1")); put("genre", kotlinx.serialization.json.JsonPrimitive("Rock"))
            put("composer", kotlinx.serialization.json.JsonPrimitive("Composer")); put("webpage_url", kotlinx.serialization.json.JsonPrimitive("https://example.org/owned"))
            put("description", kotlinx.serialization.json.JsonPrimitive("Owned fixture comment"))
        }
        suspend fun hash(item: DownloadItem, file: File) = runtime.execute(item.id, runtime.ffmpeg, listOf("-v","error","-nostdin","-i",file.path,"-map","0:a:0","-c:a","copy","-f","hash","-hash","sha256","-")).out.trim()
        val sources = AudioFormat.entries.mapIndexed { index, format -> format to if (format == AudioFormat.ORIGINAL) File(app.cacheDir,"owned-pcm.wav") else File(app.cacheDir,"format-$index/converted.${com.barebonesdownloader.android.media.audioOutput(format)!!.extension}").let { if (it.exists()) it else File(app.cacheDir,"owned-pcm.wav") } }.toMutableList()
        val webm = File(app.cacheDir,"owned-opus.webm")
        runtime.execute(UUID.randomUUID(),runtime.ffmpeg,listOf("-v","error","-nostdin","-y","-i",File(app.cacheDir,"owned-pcm.wav").path,"-c:a","libopus",webm.path))
        sources += AudioFormat.ORIGINAL to webm
        sources.forEachIndexed { index, (format, source) ->
            val item = DownloadItem(sourceUrl="owned", extractorKey="fixture", settingsSnapshot=AppSettings(audioFormat=format))
            val before = hash(item,source)
            val tagged = processor.apply(item,source,info,File(app.cacheDir,"metadata-$index"))
            assertTrue("Unexpected metadata/RG warnings for $format: ${tagged.warnings}",tagged.warnings.isEmpty())
            val tags = writer.read(item,tagged.file)
            assertEquals("音楽 & Song",tags["title"])
            assertTrue(tags.containsKey("replaygain_track_gain")); assertTrue(tags["replaygain_track_peak"]!!.toDouble() > 0)
            assertFalse(tags.keys.any { it.contains("album_gain") || it.contains("album_peak") })
            assertEquals("Tagging changed encoded samples for $format",before,hash(item,tagged.file))
            val deselected = item.copy(id=UUID.randomUUID(),settingsSnapshot=item.settingsSnapshot.copy(metadata=MetadataSettings(emptySet()),replayGainEnabled=false))
            val cleared = processor.apply(deselected,tagged.file,info,File(app.cacheDir,"metadata-cleared-$index"))
            assertTrue(cleared.warnings.toString(),cleared.warnings.isEmpty())
            assertTrue(writer.read(deselected,cleared.file).keys.none { it in com.barebonesdownloader.android.metadata.tagKeys.values || it in com.barebonesdownloader.android.metadata.gainKeys })
            assertEquals(before,hash(deselected,cleared.file))
            Log.i("BBD_INTEGRATION","TAGS/RG verified $format .${source.extension}: selected values, deselected removal, unchanged encoded samples.")
        }
        val overheadInfo = kotlinx.serialization.json.JsonObject(info + ("description" to kotlinx.serialization.json.JsonPrimitive("x".repeat(200000))))
        val capped = DownloadItem(sourceUrl="owned",extractorKey="fixture",settingsSnapshot=AppSettings())
        val output = com.barebonesdownloader.android.media.MediaSizeLimiter(runtime,app.diagnostics).applyBytes(capped,File(app.cacheDir,"owned-pcm.wav"),File(app.cacheDir,"tag-overhead-cap"),300000) { candidate ->
            val result=processor.apply(capped,candidate,overheadInfo,File(app.cacheDir,"tag-overhead-final"))
            assertTrue(result.warnings.toString(),result.warnings.isEmpty()); result.file
        }
        assertTrue(output.length() < 300000); assertEquals("m4a",output.extension)
        val finalTags=writer.read(capped,output)
        assertEquals(200000,finalTags["comment"]!!.length)
        val measured=com.barebonesdownloader.android.metadata.ReplayGain(runtime).analyse(capped,output,File(app.cacheDir,"final-gain-check"))
        assertEquals(measured.gainDb,finalTags["replaygain_track_gain"]!!.substringBefore(' ').toDouble(),.011)
        Log.i("BBD_INTEGRATION","FINAL TAG OVERHEAD CAP verified: ${output.length()} < 300000 bytes, tags retained and gain measured from final AAC.")
    }
    @Test fun hSharePrefillsWithoutEnqueueAndJournalRecovers() = runBlocking<Unit> {
        app.ready.await()
        val before = app.database.downloads().all().size
        ui.activity.runOnUiThread {
            ui.activity.startActivity(Intent(ui.activity,MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(Intent.EXTRA_TEXT,"https://www.tiktok.com/@fixture/video/123"))
        }
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTag("links_input") and hasText("https://www.tiktok.com/@fixture/video/123", substring=true)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("links_input").assertTextContains("https://www.tiktok.com/@fixture/video/123")
        assertEquals(before,app.database.downloads().all().size)
        val prefs = app.settings.settings.first()
        val tree = DocumentFile.fromTreeUri(app,android.net.Uri.parse(prefs.audioFolderUri))!!
        val journal = PublicationJournal(app)
        val interruptedId = UUID.randomUUID()
        val interrupted = tree.createFile("application/octet-stream","owned-interrupted-$interruptedId.bin")!!
        journal.record(interruptedId,interrupted.uri.toString())
        val completedId = UUID.randomUUID()
        val completed = tree.createFile("application/octet-stream","owned-completed-$completedId.bin")!!
        app.database.downloads().insert(DownloadItem(id=completedId,sourceUrl="owned",status=DownloadStatus.COMPLETED,outputUri=completed.uri.toString()))
        journal.record(completedId,completed.uri.toString())
        journal.recover(app.downloads,app.diagnostics)
        assertFalse(interrupted.exists()); assertTrue(completed.exists())
        completed.delete()
    }
    @Test fun iOwnedHttpFixtureCompletesBothModesWhileScreenOff() = runBlocking<Unit> {
        app.ready.await()
        val bytes = File(app.cacheDir,"owned-av-fixture.mp4").readBytes()
        val server = java.net.ServerSocket(0,50,java.net.InetAddress.getByName("127.0.0.1"))
        val serving = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && !server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                launch {
                    runCatching { socket.use { connection ->
                        connection.soTimeout=10000
                        val reader=connection.getInputStream().bufferedReader()
                        val request=reader.readLine().orEmpty()
                        var range: String? = null
                        while (true) { val line=reader.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Range:",true)) range=line.substringAfter(':').trim() }
                        val start=range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull()?.coerceIn(0,bytes.lastIndex) ?: 0
                        val out=connection.getOutputStream()
                        val partial=range != null
                        out.write(("HTTP/1.1 " + (if(partial) "206 Partial Content" else "200 OK") + "\r\nContent-Type: video/mp4\r\nContent-Length: ${bytes.size-start}\r\nAccept-Ranges: bytes\r\n" + (if(partial) "Content-Range: bytes $start-${bytes.lastIndex}/${bytes.size}\r\n" else "") + "Connection: close\r\n\r\n").toByteArray())
                        if (!request.startsWith("HEAD")) { delay(3000); out.write(bytes,start,bytes.size-start) }; out.flush()
                    } }
                }
            }
        }
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val prefs=app.settings.settings.first().copy(metadata=MetadataSettings(emptySet()),replayGainEnabled=false)
        val ids=mutableListOf<UUID>()
        try {
            for ((name,kind) in listOf("owned" to DownloadKind.AUDIO,"owned" to DownloadKind.VIDEO,"owned2" to DownloadKind.VIDEO)) {
                val id=UUID.randomUUID(); ids+=id
                // Local owned fixture bypasses only public-URL admission; actual yt-dlp, FIFO,
                // service, FFmpeg, metadata removal and SAF publication all execute normally.
                app.database.downloads().insert(DownloadItem(id=id,sourceUrl="http://127.0.0.1:${server.localPort}/$name.mp4",sourceHost="127.0.0.1",extractorKey="generic",mediaId=name,
                    kind=kind,queueOrder=app.database.downloads().nextOrder(),reservationKey="generic:$name|${kind.name}",settingsSnapshot=prefs,destinationUri=prefs.folder(kind)))
            }
            withContext(Dispatchers.Main) { ui.activity.startForegroundService(Intent(app,DownloadForegroundService::class.java)) }
            withTimeout(10000) { app.downloads.items.first { rows -> rows.any { it.id in ids && it.status in activeStatuses } } }
            device.sleep()
            val done=withTimeout(180000) { app.downloads.items.first { rows -> ids.all { id -> rows.any { it.id==id && it.status in setOf(DownloadStatus.COMPLETED,DownloadStatus.FAILED,DownloadStatus.CANCELLED,DownloadStatus.SKIPPED) } } } }.filter { it.id in ids }.sortedBy { it.queueOrder }
            done.forEach { assertEquals(it.errorMessage,DownloadStatus.COMPLETED,it.status) }
            assertEquals(listOf(DownloadKind.AUDIO,DownloadKind.VIDEO,DownloadKind.VIDEO),done.map { it.kind })
            assertTrue(done.zipWithNext().all { (a,b) -> a.completedAt!! <= b.completedAt!! })
            done.forEach { DocumentFile.fromSingleUri(app,android.net.Uri.parse(it.outputUri))?.delete() }
            Log.i("BBD_INTEGRATION","SCREEN-OFF FIFO verified: actual generic HTTP extraction, Audio A / Video A / Video B, final SAF saves.")
        } finally { server.close(); serving.cancel(); device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard") }
    }
    @Test fun jOptInTikTokBothModesOrExplicitExtractorFailure() = runBlocking<Unit> {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("tiktok")=="true")
        app.ready.await()
        val url="https://www.tiktok.com/@leenabhushan/video/6748451240264420610"
        val prefs=app.settings.settings.first().copy(metadata=MetadataSettings(emptySet()),replayGainEnabled=false)
        val gateway=YtDlpGateway(app.runtime,app.diagnostics)
        val source=try { gateway.resolveSource(url) } catch(e: Exception) {
            Log.i("BBD_INTEGRATION","TIKTOK EXTRACTOR LIMITATION (metadata inspection, no download success): ${describeDownloadFailure(e).message}")
            throw e
        }
        for(kind in DownloadKind.entries) {
            val item=DownloadItem(sourceUrl=source.url,extractorKey=source.extractor,mediaId=source.id,sourceHost=source.host,kind=kind,settingsSnapshot=prefs,destinationUri=prefs.folder(kind))
            val media=gateway.download(item,File(app.cacheDir,"tiktok-${item.id}")) {}
            val storage=SafMediaStorage(app)
            val uri=storage.publish(item,media) {}; storage.completed(item.id)
            assertNotNull(uri)
            DocumentFile.fromSingleUri(app,android.net.Uri.parse(uri))?.delete()
            Log.i("BBD_INTEGRATION","TIKTOK DOWNLOAD SAVED/VERIFIED ${kind.name}")
        }
    }
    @Test fun kScaledLightAndDarkThemesRemainUsable() = runBlocking<Unit> {
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.executeShellCommand("settings put system font_scale 1.3")
            delay(1500)
            for(theme in AppThemeMode.entries) {
                app.settings.update { it.copy(theme=theme) }
                delay(500); ui.waitForIdle()
                ui.onNodeWithTag("links_input").performScrollTo().assertIsDisplayed()
                ui.onNodeWithTag("queue_button").performScrollTo().assertIsDisplayed()
                assertTrue(device.takeScreenshot(File(app.cacheDir,"theme-${theme.name.lowercase()}-scaled.png")))
            }
        } finally { device.executeShellCommand("settings put system font_scale 1.0") }
    }
    @Test fun bCancellationTerminatesNativeDescendant() = runBlocking<Unit> {
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
    @Test fun cRealSafCopyCollisionAndCancellationWithOwnedMedia() = runBlocking<Unit> {
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
                val uri = storage.publish(item, media) {}; storage.completed(item.id); saved += uri
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
    @Test fun dLiveYoutubeAudioAndVideoReachSafAsReadableMedia() = runBlocking<Unit> {
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
