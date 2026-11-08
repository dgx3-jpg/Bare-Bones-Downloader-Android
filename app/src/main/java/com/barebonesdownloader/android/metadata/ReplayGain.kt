package com.barebonesdownloader.android.metadata

import com.barebonesdownloader.android.download.NativeRuntime
import com.barebonesdownloader.android.domain.model.DownloadItem
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

data class TrackGain(val integratedLufs: Double, val peak: Double) {
    val gainDb get() = -18.0 - integratedLufs
    fun forExtension(extension: String): Map<String,String> = buildMap {
        put("replaygain_track_gain", String.format(Locale.ROOT, "%+.2f dB", gainDb))
        put("replaygain_track_peak", String.format(Locale.ROOT, "%.8f", peak))
        if (extension.equals("opus", true)) put("r128_track_gain", ((-23.0 - integratedLufs) * 256).roundToInt().toString())
    }
}
class ReplayGain(private val runtime: NativeRuntime) {
    suspend fun analyse(item: DownloadItem, file: File, directory: File): TrackGain {
        directory.mkdirs()
        val loudness = File(directory, "loudness.txt"); val peak = File(directory, "peak.txt")
        // Fixed app-private paths only. ametadata emits full precision values, not rounded summary logs.
        fun path(f: File) = f.absolutePath.replace("\\", "\\\\").replace(":", "\\:").replace("'", "'\\''")
        val filter = "ebur128=metadata=1,astats=metadata=1:reset=0,ametadata=print:key=lavfi.r128.I:file='${path(loudness)}',ametadata=print:key=lavfi.astats.Overall.Peak_level:file='${path(peak)}'"
        try {
            runtime.execute(item.id, runtime.ffmpeg, listOf("-v","error","-nostdin","-i",file.path,"-map","0:a:0","-vn","-af",filter,"-f","null","-"))
            fun last(file: File, key: String): Double? = RandomAccessFile(file,"r").use { reader ->
                val count = minOf(reader.length(), 65536).toInt(); reader.seek(reader.length() - count)
                val bytes = ByteArray(count); reader.readFully(bytes)
                bytes.toString(Charsets.UTF_8).lineSequence().filter { it.startsWith("$key=") }.lastOrNull()?.substringAfter('=')?.toDoubleOrNull()
            }
            val i = last(loudness,"lavfi.r128.I") ?: error("No EBU R128 integrated loudness measurement.")
            val p = last(peak,"lavfi.astats.Overall.Peak_level") ?: error("No sample peak measurement.")
            require(i.isFinite() && i > -70 && p.isFinite()) { "ReplayGain is undefined for silent/too-short audio; samples retained." }
            return TrackGain(i, 10.0.pow(p / 20))
        } finally { loudness.delete(); peak.delete() }
    }
}
