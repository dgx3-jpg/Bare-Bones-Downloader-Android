package com.barebonesdownloader.android
import com.barebonesdownloader.android.metadata.*
import com.barebonesdownloader.android.domain.model.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
class MetadataTest {
    @Test fun structuredCreditsWinTopicThenExistingFillOnlyMissing() {
        val info = buildJsonObject { put("track","Known song"); put("artist","Known artist"); put("description","Provided to YouTube by Label\n\nTopic song · Other artist\n\nTopic album\n\nReleased on: 2020-01-02") }
        val tags = mergeMissing(sourceTags(info,true), mapOf("title" to "Old title", "genre" to "Rock"))
        assertEquals("Known song",tags["title"]); assertEquals("Known artist",tags["artist"])
        assertEquals("Topic album",tags["album"]); assertEquals("Rock",tags["genre"])
        assertEquals("2020-01-02",tags["date"])
    }
    @Test fun ordinaryCreatorIsNotSongArtistAndCatalogueRequiresReliableEvidence() {
        val tags = sourceTags(buildJsonObject { put("title","Funny clip"); put("uploader","Creator"); put("description","A funny song") },false)
        assertNull(tags["artist"]); assertNull(tags["title"]); assertNull(tags["album"])
        assertTrue(selectCatalogue(emptyList(), "Title", "", 100.0).isEmpty())
    }
    @Test fun ambiguousAlbumsStayUnknownAndKnownFieldsArePreserved() {
        val base = mapOf("title" to "Ｓong!", "artist" to "Artist", "album" to "First", "track" to "1")
        val candidates = listOf(CatalogueTrack(base,100.0),CatalogueTrack(base + ("album" to "Other"),100.5))
        val found = selectCatalogue(candidates,"Song","Artist",100.0)
        assertEquals("Artist",found["artist"]); assertNull(found["album"])
        assertTrue(selectCatalogue(candidates,"Song","Wrong artist",100.0).isEmpty())
        assertTrue(selectCatalogue(candidates,"Song","Artist",150.0).isEmpty())
        assertEquals("Known",mergeMissing(mapOf("album" to "Known"),base)["album"])
    }
    @Test fun actualContainerControlsTagCompatibilityAndDeselection() {
        val all = tagKeys.values.associateWith { "value" }
        assertFalse(selectedTags(all, AppSettings(), "m4a").containsKey("purl"))
        val wav = selectedTags(all, AppSettings(), "wav")
        assertFalse(wav.containsKey("composer")); assertFalse(wav.containsKey("album_artist"))
        assertTrue(selectedTags(all,AppSettings(metadata = MetadataSettings(emptySet())),"mp3").isEmpty())
        assertEquals("2020-01-02", normalizeTagValue("date","20200102"))
    }
    @Test fun replayGainReferenceAndOpusR128AreDistinctAndPerTrackOnly() {
        val tags = TrackGain(-20.0,.5).forExtension("opus")
        assertEquals("+2.00 dB",tags["replaygain_track_gain"])
        assertEquals("-768",tags["r128_track_gain"])
        assertFalse(tags.keys.any { it.contains("album") })
    }
}
