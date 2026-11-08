package com.barebonesdownloader.android.metadata

import com.barebonesdownloader.android.download.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class AppleCatalogue(private val runtime: NativeRuntime, private val log: DownloadLog,
    private val fetch: suspend (String) -> String = ::fetchCatalogueJson) {
    suspend fun lookup(item: com.barebonesdownloader.android.domain.model.DownloadItem, tags: Map<String,String>, duration: Double?): Map<String,String> {
        val title = tags["title"] ?: return emptyMap(); val artist = tags["artist"] ?: return emptyMap()
        if (title.isBlank() || artist.isBlank() || duration == null) return emptyMap()
        val japanese = (title + artist).any { it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' }
        var found: Map<String,String> = emptyMap()
        for (country in if (japanese) listOf("jp","gb") else listOf("gb","jp")) {
            runtime.check(item.id)
            try {
                gate.withLock {
                    while (System.nanoTime() < nextRequest) { delay(100); runtime.check(item.id) }
                    nextRequest = System.nanoTime() + 3_100_000_000L
                }
                val url = "https://itunes.apple.com/search?media=music&entity=song&limit=25&country=$country&term=" + URLEncoder.encode("$title $artist", "UTF-8")
                val root = Json.parseToJsonElement(fetch(url)).jsonObject
                runtime.check(item.id)
                val tracks = root["results"]?.jsonArray.orEmpty().mapNotNull { entry ->
                    val o = entry.jsonObject
                    if (o.text("kind") != "song") null else CatalogueTrack(mapOf("title" to o.text("trackName").orEmpty(), "artist" to o.text("artistName").orEmpty(),
                        "album" to o.text("collectionName").orEmpty(), "date" to o.text("releaseDate").orEmpty().take(10), "track" to o.text("trackNumber").orEmpty(),
                        "disc" to o.text("discNumber").orEmpty(), "genre" to o.text("primaryGenreName").orEmpty()), o["trackTimeMillis"]?.jsonPrimitive?.doubleOrNull?.div(1000))
                }
                found = mergeMissing(found, selectCatalogue(tracks, title, artist, duration))
                if (!found["album"].isNullOrBlank()) return found
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { log.add("Metadata warning: catalogue unavailable (${e.javaClass.simpleName}); known tags retained.") }
        }
        if (found["album"].isNullOrBlank()) log.add("Metadata warning: no unambiguous catalogue album; missing fields stay empty.")
        return found
    }
    companion object { private val gate = Mutex(); private var nextRequest = 0L }
}
private suspend fun fetchCatalogueJson(url: String): String = withContext(Dispatchers.IO) {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
    connection.setRequestProperty("User-Agent", "BareBonesDownloader/0.5")
    try {
        require(connection.responseCode == 200) { "Catalogue HTTP ${connection.responseCode}" }
        connection.inputStream.use { stream ->
            val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { currentCoroutineContext().ensureActive(); val n = stream.read(buffer); if (n < 0) break; require(bytes.size() + n <= 2 * 1024 * 1024); bytes.write(buffer,0,n) }
            bytes.toString("UTF-8")
        }
    } finally { connection.disconnect() }
}
