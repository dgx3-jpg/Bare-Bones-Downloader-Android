package com.barebonesdownloader.android

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.barebonesdownloader.android.data.db.DownloadDatabase
import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class DatabaseMigrationTest {
    @Test fun versionOneHistoryAndSettingsSurviveNonDestructiveUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-history.db"; context.deleteDatabase(name)
        val old = Json.parseToJsonElement(javaClass.getResourceAsStream("/db-v1.json")!!.bufferedReader().use { it.readText() }).jsonObject["database"]!!.jsonObject
        val entity = old["entities"]!!.jsonArray.single().jsonObject
        val id = UUID.randomUUID().toString()
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            sqlite.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", "downloads"))
            old["setupQueries"]!!.jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            val values = ContentValues()
            entity["fields"]!!.jsonArray.map { it.jsonObject }.filter { it["notNull"]?.jsonPrimitive?.booleanOrNull == true }.forEach { field ->
                val column = field["columnName"]!!.jsonPrimitive.content
                if (field["affinity"]!!.jsonPrimitive.content == "INTEGER") values.put(column, 0L) else values.put(column, "")
            }
            values.put("id", id); values.put("sourceUrl", "https://www.youtube.com/watch?v=BaW_jenozKc")
            values.put("status", "COMPLETED"); values.put("kind", "AUDIO"); values.put("outputFormat", "ORIGINAL")
            values.put("settingsSnapshot", "{}"); values.put("metadataSettingsSnapshot", "{}")
            values.put("outputUri", "content://old-media/file")
            sqlite.insertOrThrow("downloads", null, values); sqlite.version = 1
        }
        val upgraded = Room.databaseBuilder(context, DownloadDatabase::class.java, name).addMigrations(DownloadDatabase.MIGRATION_1_2).build()
        try {
            val row = upgraded.downloads().all().single()
            assertEquals(id, row.id.toString()); assertEquals("content://old-media/file", row.outputUri)
            assertEquals(DownloadStatus.COMPLETED, row.status); assertEquals(AppSettings(), row.settingsSnapshot)
            assertEquals(1L, row.queueOrder); assertNull(row.reservationKey)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
