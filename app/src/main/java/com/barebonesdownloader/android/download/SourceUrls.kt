package com.barebonesdownloader.android.download

import com.barebonesdownloader.android.domain.model.DownloadItem
import java.net.URI

data class SourceReference(val url: String, val extractor: String, val id: String) {
    val identity get() = "${extractor.lowercase()}:$id"
    val host get() = URI(url).host.lowercase()
}
object SourceUrls {
    fun isYoutube(host: String) = host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".youtu.be")
    fun normalize(raw: String): String {
        val u = URI(raw.trim())
        val host = u.host?.lowercase() ?: throw IllegalArgumentException("Use a public HTTP(S) single-media link.")
        require(u.scheme?.lowercase() in setOf("http","https") && u.userInfo == null && u.port == -1) { "Use a public HTTP(S) link without credentials or custom ports." }
        if (isYoutube(host)) return YoutubeSource.parse(raw).url
        require(host.contains('.') && !host.endsWith(".local") && !host.endsWith(".localhost") && host != "localhost" &&
            !host.matches(Regex("[0-9.]+")) && !host.contains(':')) { "Local/private address links are unsupported. Use a public media page." }
        return URI("${u.scheme.lowercase()}://${u.rawAuthority.lowercase()}${u.rawPath.ifEmpty { "/" }}${u.rawQuery?.let { "?$it" }.orEmpty()}").toASCIIString()
    }
    fun youtube(raw: String): SourceReference {
        val source = YoutubeSource.parse(raw)
        return SourceReference(source.url,"youtube",source.id)
    }
    fun identity(item: DownloadItem): String = if (!item.extractorKey.isNullOrBlank() && !item.mediaId.isNullOrBlank())
        "${item.extractorKey.lowercase()}:${item.mediaId}" else youtube(item.sourceUrl).identity
}
