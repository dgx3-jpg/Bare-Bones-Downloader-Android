package com.barebonesdownloader.android
import com.barebonesdownloader.android.download.*
import com.barebonesdownloader.android.domain.model.*
import org.junit.Test
import org.junit.Assert.*
class SourceUrlsTest {
    @Test fun youtubeStaysStrictAndOtherPublicUrlsPreserveMeaningfulEscapes() {
        assertEquals("https://www.youtube.com/watch?v=BaW_jenozKc",SourceUrls.normalize("https://youtu.be/BaW_jenozKc?list=ignored"))
        assertEquals("https://soundcloud.com/artist/a%20song?x=a%2Fb",SourceUrls.normalize("https://SOUNDCLOUD.COM/artist/a%20song?x=a%2Fb#fragment"))
        listOf("https://youtube.com/playlist?list=PL1","https://youtube.com/@channel","file:///tmp/file","https://user:password@example.com/a","http://localhost/a","https://127.0.0.1/a","https://example.com:123/a").forEach { assertTrue(it,runCatching { SourceUrls.normalize(it) }.isFailure) }
    }
    @Test fun identitiesAreNamespacedAndRetriedFromPersistedIdentity() {
        val a = SourceReference("https://example.com/a","TikTok","123")
        val b = SourceReference("https://soundcloud.com/b","Soundcloud","123")
        assertNotEquals(a.identity,b.identity)
        assertEquals("tiktok:123",SourceUrls.identity(DownloadItem(sourceUrl=a.url,extractorKey=a.extractor,mediaId=a.id)))
    }
}
