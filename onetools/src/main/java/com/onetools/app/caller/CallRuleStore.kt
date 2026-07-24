package com.onetools.app.caller

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

private val Context.callerRulesStore by preferencesDataStore("one_caller_rules")

class CallRuleStore(private val context: Context) {
    private val legacyKey = stringSetPreferencesKey("rules_json")
    private val migratedKey = booleanPreferencesKey("room_migrated_v1")
    private val mutex = Mutex()

    private val db: CallerDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            CallerDatabase::class.java,
            "onecaller.db",
        ).fallbackToDestructiveMigration().build()
    }

    private val dao get() = db.callRuleDao()

    val rules: Flow<List<CallRule>> = flow {
        ensureMigrated()
        emitAll(dao.observeAll().map { list -> list.map { it.toModel() } })
    }

    suspend fun snapshot(): List<CallRule> {
        ensureMigrated()
        return dao.all().map { it.toModel() }
    }

    suspend fun upsert(rule: CallRule) {
        ensureMigrated()
        dao.upsert(rule.toEntity())
        notifyDirectory()
    }

    suspend fun remove(id: String) {
        ensureMigrated()
        dao.delete(id)
        notifyDirectory()
    }

    suspend fun mergeImport(imported: List<CallRule>) {
        ensureMigrated()
        dao.upsertAll(imported.map { it.toEntity() })
        notifyDirectory()
    }

    suspend fun count(): Int {
        ensureMigrated()
        return dao.count()
    }

    suspend fun lookup(number: String?): LookupResult {
        return NumberMatcher.lookup(snapshot(), number)
    }

    private suspend fun ensureMigrated() {
        mutex.withLock {
            val prefs = context.callerRulesStore.data.first()
            if (prefs[migratedKey] == true) return@withLock
            val legacy = prefs[legacyKey].orEmpty().mapNotNull { decodeLegacy(it) }
            if (legacy.isNotEmpty()) {
                dao.upsertAll(legacy.map { it.toEntity() })
            }
            context.callerRulesStore.edit { mutable ->
                mutable[migratedKey] = true
                mutable.remove(legacyKey)
            }
        }
    }

    private fun notifyDirectory() {
        runCatching {
            OneCallerDirectoryProvider.notifyChanged(context.applicationContext.contentResolver)
        }
    }

    private fun decodeLegacy(raw: String): CallRule? = runCatching {
        val o = JSONObject(raw)
        CallRule(
            id = o.getString("id"),
            pattern = o.getString("pattern"),
            kind = CallRuleKind.valueOf(o.getString("kind")),
            mode = CallMatchMode.valueOf(o.getString("mode")),
            tag = o.optString("tag", ""),
        )
    }.getOrNull()
}
