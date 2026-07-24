package com.onetools.app.updates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.updateCatalogStore: DataStore<Preferences> by preferencesDataStore("one_update_catalog")

/**
 * Persist user-managed app sources (Obtainium-like catalog).
 */
class UpdateCatalogRepository(private val context: Context) {
    private val key = stringSetPreferencesKey("tracked_json")

    val apps: Flow<List<TrackedApp>> = context.updateCatalogStore.data.map { prefs ->
        val set = prefs[key]
        if (set.isNullOrEmpty()) {
            TrackedApps.presets
        } else {
            set.mapNotNull { decode(it) }.sortedBy { it.title.lowercase() }
        }
    }

    suspend fun ensureSeeded() {
        context.updateCatalogStore.edit { prefs ->
            if (prefs[key].isNullOrEmpty()) {
                prefs[key] = TrackedApps.presets.map { encode(it) }.toSet()
            }
        }
    }

    suspend fun add(app: TrackedApp) {
        context.updateCatalogStore.edit { prefs ->
            val cur = prefs[key]?.mapNotNull { decode(it) }?.toMutableList()
                ?: TrackedApps.presets.toMutableList()
            cur.removeAll { it.id == app.id }
            cur.add(app)
            prefs[key] = cur.map { encode(it) }.toSet()
        }
    }

    suspend fun remove(id: String) {
        context.updateCatalogStore.edit { prefs ->
            val cur = prefs[key]?.mapNotNull { decode(it) }?.toMutableList() ?: return@edit
            cur.removeAll { it.id == id }
            prefs[key] = cur.map { encode(it) }.toSet()
        }
    }

    suspend fun update(app: TrackedApp) = add(app)

    suspend fun replaceAll(apps: List<TrackedApp>) {
        context.updateCatalogStore.edit { prefs ->
            prefs[key] = apps.map { encode(it) }.toSet()
        }
    }

    suspend fun mergeAll(apps: List<TrackedApp>) {
        context.updateCatalogStore.edit { prefs ->
            val cur = prefs[key]?.mapNotNull { decode(it) }?.toMutableList()
                ?: TrackedApps.presets.toMutableList()
            val byId = cur.associateBy { it.id }.toMutableMap()
            apps.forEach { byId[it.id] = it }
            prefs[key] = byId.values.map { encode(it) }.toSet()
        }
    }

    private fun encode(app: TrackedApp): String {
        return JSONObject()
            .put("id", app.id)
            .put("title", app.title)
            .put("packageName", app.packageName)
            .put("owner", app.githubOwner)
            .put("repo", app.githubRepo)
            .put("prefer", JSONArray(app.assetPrefer))
            .put("note", app.note)
            .put("source", app.source.name)
            .put("host", app.host)
            .toString()
    }

    private fun decode(raw: String): TrackedApp? = runCatching {
        val o = JSONObject(raw)
        val prefer = o.optJSONArray("prefer")
        val list = buildList {
            if (prefer != null) {
                for (i in 0 until prefer.length()) add(prefer.getString(i))
            }
        }.ifEmpty { listOf(".apk") }
        TrackedApp(
            id = o.getString("id"),
            title = o.getString("title"),
            packageName = if (o.has("packageName") && !o.isNull("packageName")) {
                o.getString("packageName").takeIf { it.isNotBlank() }
            } else {
                null
            },
            githubOwner = o.getString("owner"),
            githubRepo = o.getString("repo"),
            assetPrefer = list,
            note = o.optString("note", ""),
            source = runCatching {
                AppSource.valueOf(o.optString("source", AppSource.GITHUB.name))
            }.getOrDefault(AppSource.GITHUB),
            host = if (o.has("host") && !o.isNull("host")) {
                o.getString("host").takeIf { it.isNotBlank() }
            } else {
                null
            },
        )
    }.getOrNull()
}
