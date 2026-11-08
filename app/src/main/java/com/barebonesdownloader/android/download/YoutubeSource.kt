package com.barebonesdownloader.android.download

import java.net.URI
import java.net.URLDecoder

/** Strict single-video desktop path. Other extractors are a milestone-6 extension. */
data class YoutubeSource(val id: String) {
    val url get() = "https://www.youtube.com/watch?v=$id"
    val identity get() = "youtube:$id"
    companion object {
        fun parse(text: String): YoutubeSource {
            val uri = runCatching { URI(text.trim()) }.getOrElse { throw IllegalArgumentException("Enter a complete HTTP or HTTPS YouTube link.") }
            require(uri.scheme?.lowercase() in setOf("http", "https") && uri.userInfo == null) { "Only HTTP(S) links without user information are accepted." }
            require(uri.port == -1 || uri.port == if (uri.scheme.equals("https", true)) 443 else 80) { "Non-standard YouTube ports are not supported." }
            val host = uri.host?.lowercase().orEmpty()
            require(host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")) { "This milestone supports YouTube single videos only." }
            val path = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
            val id = when {
                host == "youtu.be" && path.size == 1 -> path[0]
                host != "youtu.be" && path.size == 1 && path[0].equals("watch", true) ->
                    uri.rawQuery.orEmpty().split('&').firstOrNull { decode(it.substringBefore('=')).equals("v", true) }?.substringAfter('=', "")
                host != "youtu.be" && path.size == 2 && path[0].equals("shorts", true) -> path[1]
                else -> null
            }?.let(::decode)
            require(id != null && Regex("[A-Za-z0-9_-]{11}").matches(id)) { "Paste one watch, Shorts or youtu.be video link; channels, live URLs and playlist-only links are unsupported." }
            return YoutubeSource(id)
        }
        private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
    }
}
