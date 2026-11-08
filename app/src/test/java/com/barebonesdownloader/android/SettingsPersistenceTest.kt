package com.barebonesdownloader.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.barebonesdownloader.android.data.settings.DataStoreSettingsRepository
import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsPersistenceTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun allV2SettingsSurviveDataStoreReopen() = runBlocking {
        val file = temp.root.resolve("settings.preferences_pb")
        val job = SupervisorJob()
        val first = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        assertEquals(AppSettings(), first.settings.first())
        val expected = AppSettings(audioFolderUri = "content://audio", audioFolderName = "Music", videoFolderUri = "content://video", videoFolderName = "Movies",
            theme = AppThemeMode.DARK, audioFormat = AudioFormat.MP3, qualityMode = QualityMode.BITRATE, variableQuality = 2, bitrateKbps = 256,
            metadata = MetadataSettings(setOf(MetadataField.COMPOSER)), replayGainEnabled = false,
            audioMaxSizeMb = 8, videoMaxSizeMb = 128, videoMaxHeight = 1080, appendSourceUrl = true)
        first.update { expected }; job.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val second = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }))
            assertEquals(expected, second.settings.first())
        } finally { secondJob.cancelAndJoin() }
    }
}
