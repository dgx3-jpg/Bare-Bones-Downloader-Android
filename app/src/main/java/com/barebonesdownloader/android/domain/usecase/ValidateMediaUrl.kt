package com.barebonesdownloader.android.domain.usecase

import java.net.URI

fun validMediaUrl(text: String): String? {
    val value = text.trim()
    return runCatching {
        val uri = URI(value)
        value.takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    }.getOrNull()
}
