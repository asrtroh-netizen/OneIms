package com.onetools.app.caller

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.callerRulesStore by preferencesDataStore("one_caller_rules")

class CallRuleStore(private val context: Context) {
    private val key = stringSetPreferencesKey("rules_json")

    val rules: Flow<List<CallRule>> = context.callerRulesStore.data.map { prefs ->
        prefs[key].orEmpty().mapNotNull { decode(it) }
    }

    suspend fun snapshot(): List<CallRule> = rules.first()

    suspend fun upsert(rule: CallRule) {
        context.callerRulesStore.edit { prefs ->
            val cur = prefs[key].orEmpty().mapNotNull { decode(it) }.toMutableList()
            cur.removeAll { it.id == rule.id || (it.pattern == rule.pattern && it.mode == rule.mode) }
            cur.add(rule)
            prefs[key] = cur.map { encode(it) }.toSet()
        }
    }

    suspend fun remove(id: String) {
        context.callerRulesStore.edit { prefs ->
            val cur = prefs[key].orEmpty().mapNotNull { decode(it) }.toMutableList()
            cur.removeAll { it.id == id }
            prefs[key] = cur.map { encode(it) }.toSet()
        }
    }

    suspend fun mergeImport(imported: List<CallRule>) {
        context.callerRulesStore.edit { prefs ->
            val byKey = prefs[key].orEmpty().mapNotNull { decode(it) }
                .associateBy { "${it.kind}:${it.mode}:${NumberMatcher.digits(it.pattern)}" }
                .toMutableMap()
            imported.forEach { r ->
                byKey["${r.kind}:${r.mode}:${NumberMatcher.digits(r.pattern)}"] = r
            }
            prefs[key] = byKey.values.map { encode(it) }.toSet()
        }
    }

    private fun encode(r: CallRule): String = JSONObject()
        .put("id", r.id)
        .put("pattern", r.pattern)
        .put("kind", r.kind.name)
        .put("mode", r.mode.name)
        .put("tag", r.tag)
        .toString()

    private fun decode(raw: String): CallRule? = runCatching {
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
