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

    /** 胶囊缩放 0.7～1.6，默认 1.0 */
    var capsuleScale: Float
        get() = prefs.getFloat(KEY_SCALE, 1f).coerceIn(0.7f, 1.6f)
        set(value) = prefs.edit().putFloat(KEY_SCALE, value.coerceIn(0.7f, 1.6f)).apply()

    /** 相对屏幕中心的左右偏移（dp，负左正右） */
    var capsuleOffsetXDp: Int
        get() = prefs.getInt(KEY_OFFSET_X, 0).coerceIn(-120, 120)
        set(value) = prefs.edit().putInt(KEY_OFFSET_X, value.coerceIn(-120, 120)).apply()

    /** 相对默认顶栏位置的上下偏移（dp，负上正下） */
    var capsuleOffsetYDp: Int
        get() = prefs.getInt(KEY_OFFSET_Y, 0).coerceIn(-40, 120)
        set(value) = prefs.edit().putInt(KEY_OFFSET_Y, value.coerceIn(-40, 120)).apply()

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
        private const val KEY_SCALE = "capsule_scale"
        private const val KEY_OFFSET_X = "capsule_offset_x"
        private const val KEY_OFFSET_Y = "capsule_offset_y"
    }
}

data class LiveStatusPrefsSnapshot(
    val masterEnabled: Boolean,
    val capsuleEnabled: Boolean,
    val enabledSources: Set<LiveStatusSource>,
)
