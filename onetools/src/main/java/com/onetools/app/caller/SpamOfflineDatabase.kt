package com.onetools.app.caller

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Offline spam pack (Telo mast.db–shaped, clean-room).
 * Installed by [SpamSyncRepository] as a replaceable file `onespam.db`.
 */
@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "tag")
    val tag: String,
    @ColumnInfo(name = "source")
    val source: String = "",
)

@Entity(tableName = "metadata")
data class SpamMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
)

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers WHERE phone_number = :phoneNumber LIMIT 1")
    fun search(phoneNumber: String): SpamNumberEntity?

    @Query("SELECT COUNT(*) FROM spam_numbers")
    fun rowCount(): Long
}

@Dao
interface SpamMetadataDao {
    @Query("SELECT value FROM metadata WHERE key = 'version' LIMIT 1")
    fun getVersion(): String?
}

@Database(
    entities = [SpamNumberEntity::class, SpamMetadataEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SpamOfflineDatabase : RoomDatabase() {
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun metadataDao(): SpamMetadataDao

    companion object {
        const val DB_FILE_NAME = "onespam.db"
    }
}
