package com.barebonesdownloader.android.download

import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadLog(private val backingFile: File? = null) {
    private val state = MutableStateFlow(backingFile?.let { file -> runCatching {
        if (file.exists()) AtomicFile(file).readFully().toString(Charsets.UTF_8).takeLast(60_000) else ""
    }.getOrDefault("") }.orEmpty())
    val text = state.asStateFlow()
    private val changes = Channel<String>(Channel.CONFLATED)
    init {
        if (backingFile != null) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (value in changes) runCatching {
                backingFile.parentFile?.mkdirs()
                val atomic = AtomicFile(backingFile); val output = atomic.startWrite()
                try { output.write(value.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
                catch (t: Throwable) { atomic.failWrite(output); throw t }
            }
        }
    }
    @Synchronized fun add(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
        state.value = (state.value + "[$stamp] ${sanitizeDiagnostic(message).take(8000)}\n").takeLast(60_000)
        if (backingFile != null) changes.trySend(state.value)
    }
}
