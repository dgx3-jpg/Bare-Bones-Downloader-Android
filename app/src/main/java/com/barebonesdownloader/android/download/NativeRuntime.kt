package com.barebonesdownloader.android.download

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Own session per command: SIGKILL targets the process group, including downloader children. */
class NativeRuntime(private val context: Context) {
    data class Result(val out: String, val err: String)
    private class Slot(val process: Process) { @Volatile var group: Int = 0 }
    private val running = ConcurrentHashMap<UUID, Slot>()
    private val cancelled = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile private var initialized = false
    private val native get() = File(context.applicationInfo.nativeLibraryDir)
    private val packages get() = File(context.noBackupFilesDir, "youtubedl-android/packages")
    val python get() = File(native, "libpython.so")
    val ffmpeg get() = File(native, "libffmpeg.so")
    val ffprobe get() = File(native, "libffprobe.so")
    val quickJs get() = File(native, "libqjs.so")
    val ytdlp get() = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
    @Synchronized fun initialize() {
        if (initialized) return
        YoutubeDL.init(context); FFmpeg.init(context)
        require(listOf(python, ffmpeg, ffprobe, quickJs, ytdlp).all { it.isFile && it.length() > 0 }) { "Native runtime is missing for this device ABI. Reinstall the matching APK." }
        initialized = true
    }
    fun reset(id: UUID) { cancelled.remove(id) }
    fun cancel(id: UUID) { cancelled.add(id); running[id]?.let(::kill) }
    fun cancelAll() { running.keys.forEach(::cancel) }
    fun check(id: UUID) { if (id in cancelled) throw CancellationException("Cancelled") }
    private fun killGroup(slot: Slot) { if (slot.group > 0) runCatching { Os.kill(-slot.group, OsConstants.SIGKILL) } }
    private fun kill(slot: Slot) {
        killGroup(slot)
        slot.process.destroyForcibly()
    }
    suspend fun execute(id: UUID, executable: File, args: List<String>, timeoutSeconds: Long = 7200, onLine: (String) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        initialize(); check(id)
        val launcher = "import os,sys; os.setsid(); print('BBD_PID='+str(os.getpid()),flush=True); assert sys.stdin.buffer.read(1)==b'1'; os.execv(sys.argv[1],sys.argv[1:])"
        val builder = ProcessBuilder(listOf(python.absolutePath, "-c", launcher, executable.absolutePath) + args)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", "${File(packages, "python/usr/lib")}:${File(packages, "ffmpeg/usr/lib")}")
            put("PYTHONHOME", File(packages, "python/usr").absolutePath)
            put("SSL_CERT_FILE", File(packages, "python/usr/etc/tls/cert.pem").absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("PATH", "${native.absolutePath}:${System.getenv("PATH").orEmpty()}")
        }
        val process = builder.start(); val slot = Slot(process); running[id] = slot
        val out = StringBuilder(); val err = StringBuilder()
        fun append(buffer: StringBuilder, line: String, limit: Int) = synchronized(buffer) {
            if (buffer.length < limit) buffer.append(line.take(limit - buffer.length)).append('\n')
        }
        val stdout = Thread {
            runCatching { process.inputStream.bufferedReader().use { reader ->
                val header = reader.readLine().orEmpty()
                if (header.startsWith("BBD_PID=")) {
                    slot.group = header.substringAfter('=').toIntOrNull()?.takeIf { it > 0 } ?: 0
                    if (id in cancelled || slot.group <= 0) kill(slot)
                    else process.outputStream.use { it.write(49); it.flush() }
                } else {
                    append(err, "Native launcher did not establish a process group: $header", 65_536)
                    kill(slot)
                }
                reader.lineSequence().forEach { line ->
                    append(out, line, 4_000_000)
                    runCatching { onLine(line) }.onFailure { append(err, "Progress callback failed: ${it.javaClass.simpleName}", 65_536) }
                }
            } }.onFailure { append(err, "Output stream closed: ${it.javaClass.simpleName}", 65_536) }
        }.apply { name = "download-stdout"; start() }
        val stderr = Thread { runCatching { process.errorStream.bufferedReader().use { reader -> reader.lineSequence().forEach { append(err, it, 65_536) } } } }
            .apply { name = "download-stderr"; start() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive(); check(id)
                check(System.nanoTime() < deadline) { "The native command exceeded its time limit. Retry a shorter item." }
            }
            // A completed tool has no legitimate unfinished children. Close inherited
            // child pipes before draining, without closing the parent's unread bytes.
            killGroup(slot)
            stdout.join(5000); stderr.join(5000)
            check(!stdout.isAlive && !stderr.isAlive) { "Native output did not finish draining. Retry this item." }
            currentCoroutineContext().ensureActive(); check(id)
            check(slot.group > 0) { "Native process isolation failed. No output was published." }
            check(process.exitValue() == 0) { sanitizeDiagnostic(err.toString().ifBlank { out.toString() }).ifBlank { "Media tool failed (exit ${process.exitValue()})." } }
            Result(out.toString(), err.toString())
        } finally { kill(slot); running.remove(id, slot); stdout.join(2000); stderr.join(2000) }
    }
}
fun sanitizeDiagnostic(value: String): String = value.replace(Regex("https?://[^\\s]+"), "[URL]").takeLast(4000)
