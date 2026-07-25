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
    /** Estimated full capacity from this charge sample; 0 if N/A */
    val estimatedFullMah: Int,
    val note: String = "",
)

@Dao
interface BatterySessionDao {
    @Query("SELECT * FROM battery_sessions ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<BatterySessionEntity>>

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

    @Query("DELETE FROM battery_sessions")
    suspend fun clear()
}

@Database(entities = [BatterySessionEntity::class], version = 1, exportSchema = false)
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

    suspend fun upsert(entity: BatterySessionEntity) = dao.upsert(entity)

    suspend fun recentEstimates(limit: Int = 30): List<Int> = dao.recentChargeEstimates(limit)

    suspend fun clear() = dao.clear()
}
