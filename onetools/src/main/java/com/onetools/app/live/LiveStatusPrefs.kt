package com.onetools.app.live

import android.content.Context
import android.content.SharedPreferences

class LiveStatusPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER, false)
        set(value) = prefs.edit().putBoolean(KEY_MASTER, value).apply()

    fun isSourceEnabled(source: LiveStatusSource): Boolean =
        prefs.getBoolean(keySource(source), true)

    fun setSourceEnabled(source: LiveStatusSource, enabled: Boolean) {
        prefs.edit().putBoolean(keySource(source), enabled).apply()
    }

    fun snapshot(): LiveStatusPrefsSnapshot = LiveStatusPrefsSnapshot(
        masterEnabled = masterEnabled,
        enabledSources = LiveStatusSource.entries.filter { isSourceEnabled(it) }.toSet(),
    )

    private fun keySource(source: LiveStatusSource): String = "src_${source.id}"

    companion object {
        private const val PREFS = "onetools_live_status"
        private const val KEY_MASTER = "master_enabled"
    }
}

data class LiveStatusPrefsSnapshot(
    val masterEnabled: Boolean,
    val enabledSources: Set<LiveStatusSource>,
)
