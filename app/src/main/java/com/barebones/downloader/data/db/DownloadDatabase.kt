package com.barebones.downloader.data.db

import androidx.room.*
import com.barebones.downloader.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DownloadConverters {
    @TypeConverter fun uuid(value: String): UUID = UUID.fromString(value)
    @TypeConverter fun uuid(value: UUID): String = value.toString()
    @TypeConverter fun metadata(value: String): MetadataSettings = Json.decodeFromString(value)
    @TypeConverter fun metadata(value: MetadataSettings): String = Json.encodeToString(value)
}
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt ASC, id ASC") fun observeAll(): Flow<List<DownloadItem>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: DownloadItem)
    @Update suspend fun update(item: DownloadItem)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: UUID)
    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')") suspend fun clearHistory()
}
@Database(entities = [DownloadItem::class], version = 1, exportSchema = true)
@TypeConverters(DownloadConverters::class)
abstract class DownloadDatabase : RoomDatabase() { abstract fun downloads(): DownloadDao }
