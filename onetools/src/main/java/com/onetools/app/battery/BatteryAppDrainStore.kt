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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Entity(tableName = "battery_app_drain")
data class BatteryAppDrainEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val packageName: String,
    val label: String,
    val mahTotal: Double,
    val mahScreenOn: Double,
    val mahScreenOff: Double,
    val updatedAt: Long,
)

@Dao
interface BatteryAppDrainDao {
    @Query(
        """
        SELECT * FROM battery_app_drain
        WHERE dayKey = :dayKey
        ORDER BY mahTotal DESC
        """,
    )
    fun observeDay(dayKey: String): Flow<List<BatteryAppDrainEntity>>

    @Query("SELECT * FROM battery_app_drain WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BatteryAppDrainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BatteryAppDrainEntity)

    @Query("DELETE FROM battery_app_drain WHERE dayKey < :dayKey")
    suspend fun deleteBefore(dayKey: String)
}

@Database(entities = [BatteryAppDrainEntity::class], version = 1, exportSchema = false)
abstract class BatteryAppDrainDatabase : RoomDatabase() {
    abstract fun dao(): BatteryAppDrainDao
}

class BatteryAppDrainStore(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        BatteryAppDrainDatabase::class.java,
        "one_battery_app_drain.db",
    ).fallbackToDestructiveMigration().build()

    private val dao get() = db.dao()

    fun observeToday(): Flow<List<BatteryAppDrainEntity>> = dao.observeDay(todayKey())

    suspend fun attribute(
        packageName: String,
        label: String,
        mah: Double,
        screenOn: Boolean,
    ) {
        if (mah <= 0) return
        val day = todayKey()
        val id = "$day|$packageName"
        val prev = dao.get(id)
        val on = if (screenOn) mah else 0.0
        val off = if (screenOn) 0.0 else mah
        dao.upsert(
            BatteryAppDrainEntity(
                id = id,
                dayKey = day,
                packageName = packageName,
                label = label.ifBlank { packageName },
                mahTotal = (prev?.mahTotal ?: 0.0) + mah,
                mahScreenOn = (prev?.mahScreenOn ?: 0.0) + on,
                mahScreenOff = (prev?.mahScreenOff ?: 0.0) + off,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        fun todayKey(now: Long = System.currentTimeMillis()): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            return fmt.format(Date(now))
        }
    }
}
