package com.barebonesdownloader.android

import com.barebonesdownloader.android.download.*
import com.barebonesdownloader.android.storage.FilenamePolicy
import com.barebonesdownloader.android.domain.model.*
import com.barebonesdownloader.android.media.videoSelector
import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun strictParserNormalizesDesktopVariantsAndDropsPlaylistContext() {
        val expected = YoutubeSource("BaW_jenozKc")
        listOf("https://youtu.be/BaW_jenozKc?t=3", "https://m.youtube.com/shorts/BaW_jenozKc", "https://youtube.com/watch?v=BaW_jenozKc&list=PL123", "http://YOUTUBE.COM/watch?v=BaW_jenozKc").forEach {
            assertEquals(expected, YoutubeSource.parse(it)); assertEquals("youtube:BaW_jenozKc", YoutubeSource.parse(it).identity)
        }
    }
    @Test fun strictParserRejectsCollectionsSpoofedHostsLiveAndMalformedIds() {
        listOf("https://youtube.com/playlist?list=PL123", "https://youtube.com/@channel", "https://youtube.com/live/BaW_jenozKc", "https://youtube.com.evil.test/watch?v=BaW_jenozKc", "https://youtube.com:123/watch?v=BaW_jenozKc", "https://user@youtube.com/watch?v=BaW_jenozKc", "file:///tmp/a", "https://youtu.be/a", "https://youtu.be/BaW_jenozKc/other", "https://soundcloud.com/a").forEach {
            assertTrue(it, runCatching { YoutubeSource.parse(it) }.isFailure)
        }
    }
    @Test fun safeFilenamesPreserveUnicodeAndKeepTypeSpecificIdPolicy() {
        val audio = DownloadItem(sourceUrl = "https://youtube.com/watch?v=BaW_jenozKc", mediaId = "BaW_jenozKc")
        val title = "../音楽 🎵/title:?."
        val name = FilenamePolicy.name(audio, title, "m4a")
        assertTrue(name.contains("音楽")); assertFalse(name.contains('/')); assertFalse(name.contains("BaW_jenozKc"))
        assertTrue(FilenamePolicy.name(audio.copy(kind = DownloadKind.VIDEO), title, "mp4").contains("[BaW_jenozKc]"))
        assertTrue(FilenamePolicy.clean("界".repeat(100)).toByteArray().size <= 180)
        assertEquals("_CON", FilenamePolicy.clean("CON"))
        assertFalse(sanitizeDiagnostic("Error https://secret.example/path?token=private failed").contains("private"))
    }
    @Test fun authenticationFailureDoesNotSuggestUnsupportedCookieImports() {
        val problem = describeDownloadFailure(IllegalStateException("Sign in to confirm you are not a bot. Use --cookies https://example.test/?token=secret"))
        assertEquals("AUTH_REQUIRED", problem.code); assertFalse(problem.message.contains("--cookies"))
        assertFalse(problem.diagnostic.contains("secret"))
        assertEquals("NO_SPACE", describeDownloadFailure(IllegalStateException("No space left on device")).code)
        assertEquals("TIMEOUT", describeDownloadFailure(IllegalStateException("Socket timed out")).code)
    }
    @Test fun videoSelectorRequiresBothCompatibleStreamsAndAppliesHeightToBothPaths() {
        val choice = videoSelector(720)
        assertTrue(choice.contains("bestaudio[acodec^=mp4a]")); assertEquals(2, Regex("height<=720").findAll(choice).count())
    }
}
