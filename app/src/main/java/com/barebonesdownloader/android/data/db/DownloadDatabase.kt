package com.barebonesdownloader.android.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DownloadConverters {
    @TypeConverter fun uuid(value: String): UUID = UUID.fromString(value)
    @TypeConverter fun uuid(value: UUID): String = value.toString()
    @TypeConverter fun settings(value: String): AppSettings = Json.decodeFromString(value)
    @TypeConverter fun settings(value: AppSettings): String = Json.encodeToString(value)
    @TypeConverter fun metadata(value: String): MetadataSettings = Json.decodeFromString(value)
    @TypeConverter fun metadata(value: MetadataSettings): String = Json.encodeToString(value)
}
@Dao interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY queueOrder, createdAt, id") fun observeAll(): Flow<List<DownloadItem>>
    @Query("SELECT * FROM downloads ORDER BY queueOrder, createdAt, id") suspend fun all(): List<DownloadItem>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: UUID): DownloadItem?
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE reservationKey = :key)") suspend fun reserved(key: String): Boolean
    @Query("SELECT COALESCE(MAX(queueOrder), 0) + 1 FROM downloads") suspend fun nextOrder(): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: DownloadItem)
    @Update suspend fun update(item: DownloadItem)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: UUID)
    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED')") suspend fun clearHistory()
}
@Database(entities = [DownloadItem::class], version = 2, exportSchema = true)
@TypeConverters(DownloadConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN queueOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN reservationKey TEXT")
                db.execSQL("UPDATE downloads SET queueOrder = rowid")
                db.execSQL("CREATE UNIQUE INDEX index_downloads_reservationKey ON downloads(reservationKey)")
            }
        }
    }
}
