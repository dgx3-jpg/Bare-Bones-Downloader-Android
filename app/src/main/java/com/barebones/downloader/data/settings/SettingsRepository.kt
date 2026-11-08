package com.barebones.downloader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.barebones.downloader.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.downloaderSettings by preferencesDataStore(name = "downloader_settings")
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val key = stringPreferencesKey("settings_v1")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(value: String?): AppSettings = value?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings()
    override val settings = store.data.map { decode(it[key]) }
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { it[key] = json.encodeToString(transform(decode(it[key]))) }
    }
}
