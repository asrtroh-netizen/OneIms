package com.onetools.app.battery

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "battery_sessions")
data class BatterySessionEntity(
    @PrimaryKey val id: String,
    /** CHARGE or DISCHARGE */
    val kind: String,
    val startedAt: Long,
    val endedAt: Long,
    val startPercent: Int,
    val endPercent: Int,
    val startChargeMah: Int,
    val endChargeMah: Int,
    /** Estimated full capacity from charge sample; 0 if N/A */
    val estimatedFullMah: Int,
    /** Deep-sleep share estimate 0–100; -1 unknown */
    val deepSleepPercent: Int = -1,
    val screenOffMs: Long = 0L,
    val deepSleepMs: Long = 0L,
    val note: String = "",
)

@Entity(tableName = "battery_samples")
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val at: Long,
    val percent: Int,
    val chargeMah: Int,
    val screenOn: Boolean,
)

@Dao
interface BatterySessionDao {
    @Query("SELECT * FROM battery_sessions ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<BatterySessionEntity>>

    @Query("SELECT * FROM battery_sessions WHERE kind = :kind ORDER BY endedAt DESC")
    fun observeKind(kind: String): Flow<List<BatterySessionEntity>>

    @Query("SELECT * FROM battery_sessions ORDER BY endedAt DESC")
    suspend fun all(): List<BatterySessionEntity>

    @Query(
        """
        SELECT estimatedFullMah FROM battery_sessions
        WHERE kind = 'CHARGE' AND estimatedFullMah > 0
        ORDER BY endedAt DESC LIMIT :limit
        """,
    )
    suspend fun recentChargeEstimates(limit: Int = 30): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BatterySessionEntity)

    @Insert
    suspend fun insertSample(sample: BatterySampleEntity)

    @Query("SELECT * FROM battery_samples WHERE sessionId = :sessionId ORDER BY at ASC")
    suspend fun samplesFor(sessionId: String): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_samples WHERE sessionId = :sessionId ORDER BY at ASC")
    fun observeSamples(sessionId: String): Flow<List<BatterySampleEntity>>

    @Query("DELETE FROM battery_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM battery_samples")
    suspend fun clearSamples()
}

@Database(
    entities = [BatterySessionEntity::class, BatterySampleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class BatterySessionDatabase : RoomDatabase() {
    abstract fun dao(): BatterySessionDao
}

class BatterySessionStore(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        BatterySessionDatabase::class.java,
        "one_battery_sessions.db",
    ).fallbackToDestructiveMigration().build()

    private val dao get() = db.dao()

    val sessions: Flow<List<BatterySessionEntity>> = dao.observeAll()
    val dischargeSessions: Flow<List<BatterySessionEntity>> = dao.observeKind("DISCHARGE")

    suspend fun upsert(entity: BatterySessionEntity) = dao.upsert(entity)

    suspend fun addSample(sample: BatterySampleEntity) = dao.insertSample(sample)

    suspend fun samplesFor(sessionId: String): List<BatterySampleEntity> = dao.samplesFor(sessionId)

    fun observeSamples(sessionId: String): Flow<List<BatterySampleEntity>> =
        dao.observeSamples(sessionId)

    suspend fun recentEstimates(limit: Int = 30): List<Int> = dao.recentChargeEstimates(limit)

    suspend fun clear() {
        dao.clearSamples()
        dao.clearSessions()
    }
}
