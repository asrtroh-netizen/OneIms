package com.onetools.app.caller

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
import java.util.UUID

enum class ReportTag(val wire: String, val labelZh: String) {
    SPAM("spam", "骚扰电话"),
    FRAUD("fraud", "诈骗电话"),
    AGENT("agent", "中介"),
    SALES("sales", "广告推销"),
    OTHER("other", "其他骚扰"),
    /** Community correction — demotes candidates; never auto-blocks locally. */
    WRONG_TAG("wrong_tag", "标签纠错"),
    ;

    companion object {
        fun fromWire(raw: String): ReportTag =
            entries.firstOrNull { it.wire == raw || it.labelZh == raw } ?: SPAM
    }

    val appliesLocalBlock: Boolean get() = this != WRONG_TAG
}

@Entity(tableName = "local_reports")
data class LocalReportEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val tag: String,
    val note: String = "",
    val source: String = "manual",
    val createdAt: Long,
    val applyLocal: Boolean = true,
)

@Dao
interface LocalReportDao {
    @Query("SELECT * FROM local_reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalReportEntity>>

    @Query("SELECT * FROM local_reports ORDER BY createdAt DESC")
    suspend fun all(): List<LocalReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalReportEntity)

    @Query("DELETE FROM local_reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM local_reports WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): LocalReportEntity?
}

@Database(entities = [LocalReportEntity::class], version = 1, exportSchema = false)
abstract class LocalReportDatabase : RoomDatabase() {
    abstract fun dao(): LocalReportDao
}

class LocalReportStore(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        LocalReportDatabase::class.java,
        "onecaller_reports.db",
    ).fallbackToDestructiveMigration().build()

    private val dao get() = db.dao()

    val reports: Flow<List<LocalReportEntity>> = dao.observeAll()

    suspend fun snapshot(): List<LocalReportEntity> = dao.all()

    suspend fun add(
        rawPhone: String,
        tag: ReportTag,
        note: String = "",
        source: String = "manual",
        applyLocal: Boolean = true,
    ): LocalReportEntity {
        val phone = NumberMatcher.digits(rawPhone).removePrefix("86")
        require(phone.length >= 7) { "号码太短" }
        val entity = LocalReportEntity(
            id = UUID.randomUUID().toString(),
            phone = phone,
            tag = tag.wire,
            note = note.trim().take(80),
            source = source,
            createdAt = System.currentTimeMillis(),
            applyLocal = applyLocal,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun remove(id: String) {
        dao.deleteById(id)
    }

    suspend fun findByPhone(rawPhone: String): LocalReportEntity? {
        val phone = NumberMatcher.digits(rawPhone).removePrefix("86")
        return dao.findByPhone(phone)
    }
}
