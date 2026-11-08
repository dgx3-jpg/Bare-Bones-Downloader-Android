package com.barebones.downloader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.barebones.downloader.data.settings.DataStoreSettingsRepository
import com.barebones.downloader.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsPersistenceTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun allSettingsSurviveClosingAndReopeningDataStore() = runBlocking {
        val file = temp.root.resolve("settings.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file })
        val first = DataStoreSettingsRepository(firstStore)
        assertEquals(AppSettings(), first.settings.first())
        val expected = AppSettings(saveFolderUri = "content://documents/tree/music", audioFormat = AudioFormat.MP3,
            audioQuality = AudioQuality.LOW, metadata = MetadataSettings(setOf(MetadataField.ARTIST, MetadataField.SOURCE_URL)), replayGainEnabled = true)
        first.update { expected }
        firstJob.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val second = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }))
            assertEquals(expected, second.settings.first())
            second.update { it.copy(audioFormat = AudioFormat.FLAC) }
            assertEquals(expected.copy(audioFormat = AudioFormat.FLAC), second.settings.first())
        } finally { secondJob.cancelAndJoin() }
    }
}
