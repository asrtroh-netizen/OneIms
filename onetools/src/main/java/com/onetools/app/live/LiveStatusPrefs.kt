package com.onetools.app.live

import android.content.Context
import android.content.SharedPreferences

class LiveStatusPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER, false)
        set(value) = prefs.edit().putBoolean(KEY_MASTER, value).apply()

    /** 顶栏灵动岛胶囊（默认开；需悬浮窗权限）。 */
    var capsuleEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPSULE, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPSULE, value).apply()

    fun isSourceEnabled(source: LiveStatusSource): Boolean =
        prefs.getBoolean(keySource(source), true)

    fun setSourceEnabled(source: LiveStatusSource, enabled: Boolean) {
        prefs.edit().putBoolean(keySource(source), enabled).apply()
    }

    fun snapshot(): LiveStatusPrefsSnapshot = LiveStatusPrefsSnapshot(
        masterEnabled = masterEnabled,
        capsuleEnabled = capsuleEnabled,
        enabledSources = LiveStatusSource.entries.filter { isSourceEnabled(it) }.toSet(),
    )

    private fun keySource(source: LiveStatusSource): String = "src_${source.id}"

    companion object {
        private const val PREFS = "onetools_live_status"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_CAPSULE = "capsule_enabled"
    }
}

data class LiveStatusPrefsSnapshot(
    val masterEnabled: Boolean,
    val capsuleEnabled: Boolean,
    val enabledSources: Set<LiveStatusSource>,
)
