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

    /**
     * 长度（横向）缩放 0.7～1.8，默认 1.15（贴近截图扁长胶囊）。
     * 旧版单一 scale 键会迁移为宽/高初值。
     */
    var capsuleWidthScale: Float
        get() = prefs.getFloat(KEY_WIDTH, legacyScaleOr(1.15f)).coerceIn(0.7f, 1.8f)
        set(value) = prefs.edit().putFloat(KEY_WIDTH, value.coerceIn(0.7f, 1.8f)).apply()

    /** 高低（纵向）缩放 0.6～1.5，默认 0.85（扁胶囊，少挡摄像头）。 */
    var capsuleHeightScale: Float
        get() = prefs.getFloat(KEY_HEIGHT, legacyScaleOr(0.85f)).coerceIn(0.6f, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT, value.coerceIn(0.6f, 1.5f)).apply()

    /** 相对屏幕中心的左右偏移（dp，负左正右） */
    var capsuleOffsetXDp: Int
        get() = prefs.getInt(KEY_OFFSET_X, 0).coerceIn(-120, 120)
        set(value) = prefs.edit().putInt(KEY_OFFSET_X, value.coerceIn(-120, 120)).apply()

    /**
     * 上下偏移（dp）。默认 +6：整体压在状态栏下方一点，避开前置摄像头挖孔。
     * 想贴岛区可往负向拖。
     */
    var capsuleOffsetYDp: Int
        get() = prefs.getInt(KEY_OFFSET_Y, 6).coerceIn(-40, 120)
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

    private fun legacyScaleOr(fallback: Float): Float =
        if (prefs.contains(KEY_WIDTH) || prefs.contains(KEY_HEIGHT)) {
            fallback
        } else if (prefs.contains(KEY_LEGACY_SCALE)) {
            prefs.getFloat(KEY_LEGACY_SCALE, fallback)
        } else {
            fallback
        }

    private fun keySource(source: LiveStatusSource): String = "src_${source.id}"

    companion object {
        private const val PREFS = "onetools_live_status"
        private const val KEY_MASTER = "master_enabled"
        private const val KEY_CAPSULE = "capsule_enabled"
        private const val KEY_LEGACY_SCALE = "capsule_scale"
        private const val KEY_WIDTH = "capsule_width_scale"
        private const val KEY_HEIGHT = "capsule_height_scale"
        private const val KEY_OFFSET_X = "capsule_offset_x"
        private const val KEY_OFFSET_Y = "capsule_offset_y"
    }
}

data class LiveStatusPrefsSnapshot(
    val masterEnabled: Boolean,
    val capsuleEnabled: Boolean,
    val enabledSources: Set<LiveStatusSource>,
)
