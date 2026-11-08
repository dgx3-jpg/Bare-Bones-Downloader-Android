package com.barebonesdownloader.android.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadLog {
    private val state = MutableStateFlow("")
    val text = state.asStateFlow()
    fun add(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
        state.update { (it + "[$stamp] ${message.take(8000)}\n").takeLast(60_000) }
    }
}
