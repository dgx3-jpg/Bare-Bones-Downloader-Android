package com.barebonesdownloader.android.storage

import com.barebonesdownloader.android.domain.model.*
import java.nio.charset.StandardCharsets

object FilenamePolicy {
    fun clean(value: String, bytes: Int = 180): String {
        val safe = value.replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), " ").replace(Regex("\\s+"), " ").trim().trim('.')
        val name = if (safe.isBlank()) "Untitled" else safe
        val result = StringBuilder()
        for (cp in name.codePoints().toArray()) {
            val next = String(Character.toChars(cp))
            if ((result.toString() + next).toByteArray(StandardCharsets.UTF_8).size > bytes) break
            result.append(next)
        }
        val capped = result.toString().trim().trim('.').ifBlank { "Untitled" }
        return if (Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])").matches(capped)) "_$capped" else capped
    }
    fun name(item: DownloadItem, title: String, extension: String): String {
        require(Regex("[A-Za-z0-9]{1,8}").matches(extension)) { "The output extension is invalid." }
        val suffix = if (item.kind == DownloadKind.VIDEO) " [${clean(item.mediaId.orEmpty(), 24)}]" else ""
        val url = if (item.settingsSnapshot.appendSourceUrl) " ${clean(item.sourceUrl, 72)}" else ""
        return clean(title, if (url.isEmpty()) 160 else 110) + suffix + url + "." + extension.lowercase()
    }
}
