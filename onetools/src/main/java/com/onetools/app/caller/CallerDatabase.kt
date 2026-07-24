package com.onetools.app.caller

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "call_rules")
data class CallRuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val kind: String,
    val mode: String,
    val tag: String = "",
)

@Dao
interface CallRuleDao {
    @Query("SELECT * FROM call_rules ORDER BY pattern ASC")
    fun observeAll(): Flow<List<CallRuleEntity>>

    @Query("SELECT * FROM call_rules")
    suspend fun all(): List<CallRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CallRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CallRuleEntity>)

    @Query("DELETE FROM call_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM call_rules")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM call_rules WHERE mode = 'TAG' AND tag = :tag OR (mode = 'TAG' AND pattern = :tag)",
    )
    suspend fun rulesForTag(tag: String): List<CallRuleEntity>
}

@Database(entities = [CallRuleEntity::class], version = 1, exportSchema = false)
abstract class CallerDatabase : RoomDatabase() {
    abstract fun callRuleDao(): CallRuleDao
}

fun CallRuleEntity.toModel(): CallRule = CallRule(
    id = id,
    pattern = pattern,
    kind = CallRuleKind.valueOf(kind),
    mode = CallMatchMode.valueOf(mode),
    tag = tag,
)

fun CallRule.toEntity(): CallRuleEntity = CallRuleEntity(
    id = id,
    pattern = pattern,
    kind = kind.name,
    mode = mode.name,
    tag = tag,
)
