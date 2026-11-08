package com.barebonesdownloader.android.media

import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.download.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.floor

class SizeLimitImpossible : IllegalStateException("The media cannot fit strictly below the selected size cap. Increase or disable the cap; nothing was published.")
object SizeBudget {
    fun bytes(mb: Int): Long { require(mb in 0..16384); return mb * 1_000_000L }
    fun fits(size: Long, maximum: Long) = maximum == 0L || size < maximum
    fun estimate(bytes: Long, duration: Double, minimum: Int, maximum: Int): Int {
        require(duration.isFinite() && duration > 0) { "Cannot determine duration for the size cap." }
        return floor(bytes * .88 * 8 / duration / 1000).coerceIn(minimum.toDouble(), maximum.toDouble()).toInt()
    }
    fun lower(value: Int, minimum: Int) = maxOf(minimum, minOf(value - 1, floor(value * .8).toInt()))
}
class MediaSizeLimiter(private val runtime: NativeRuntime, private val log: DownloadLog) {
    suspend fun apply(item: DownloadItem, input: File, directory: File, finalize: suspend (File) -> File = { it }): File = applyBytes(item, input, directory,
        SizeBudget.bytes(if (item.kind == DownloadKind.AUDIO) item.settingsSnapshot.audioMaxSizeMb else item.settingsSnapshot.videoMaxSizeMb), finalize)
    suspend fun applyBytes(item: DownloadItem, input: File, directory: File, maximum: Long, finalize: suspend (File) -> File = { it }): File {
        require(maximum >= 0)
        val ready = finalize(input)
        if (SizeBudget.fits(ready.length(), maximum)) return ready
        val gateway = YtDlpGateway(runtime, log)
        val duration = gateway.probe(item, input)["format"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull ?: error("Cannot read media duration for the size cap.")
        val video = item.kind == DownloadKind.VIDEO
        val minimum = if (video) 72 else 16
        var budget = SizeBudget.estimate(maximum, duration, minimum, if (video) 50000 else 512)
        val format = item.settingsSnapshot.audioFormat.takeIf { it.bitrate } ?: AudioFormat.M4A
        val audio = audioOutput(format)!!
        directory.mkdirs()
        repeat(7) { attempt ->
            runtime.check(item.id)
            val output = File(directory, "size-$attempt.${if (video) "mp4" else audio.extension}")
            log.add("Size cap: ${item.kind.label}, attempt ${attempt + 1}/7, budget $budget kbps.")
            val args = mutableListOf("-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", input.path,
                "-map_metadata", "0", "-map_metadata:s:a:0", "0")
            if (video) {
                val a = (budget / 5).coerceIn(24, 96); val v = maxOf(48, budget - a)
                args += listOf("-map", "0:v:0", "-map", "0:a:0", "-sn", "-dn", "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-b:v", "${v}k", "-maxrate", "${v}k", "-bufsize", "${v * 2}k", "-c:a", "aac", "-b:a", "${a}k", "-movflags", "+faststart")
            } else {
                val rate = if (format == AudioFormat.MP3) listOf(32,40,48,56,64,80,96,112,128,160,192,224,256,320).lastOrNull { it <= budget } ?: 32 else minOf(budget, if (format == AudioFormat.OPUS) 256 else 512)
                args += listOf("-map", "0:a:0", "-vn", "-c:a", audio.encoder, "-b:a", "${rate}k")
            }
            try {
                runtime.execute(item.id, runtime.ffmpeg, args + output.path)
                runtime.check(item.id)
                gateway.validate(item, output)
                val finalized = finalize(output)
                if (finalized.length() > 0 && SizeBudget.fits(finalized.length(), maximum)) return finalized
            } finally { if (!output.exists() || !SizeBudget.fits(output.length(), maximum)) output.delete() }
            output.delete()
            if (budget <= minimum) throw SizeLimitImpossible()
            budget = SizeBudget.lower(budget, minimum)
        }
        throw SizeLimitImpossible()
    }
}
