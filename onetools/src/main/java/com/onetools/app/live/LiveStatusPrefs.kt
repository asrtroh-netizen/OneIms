package com.onetools.app.live

import android.content.Context
import android.content.SharedPreferences
import com.onetools.app.live.capsule.CameraAnchor
import com.onetools.app.live.capsule.CapsuleGestureAction
import com.onetools.app.live.capsule.CapsuleGestureDefaults
import com.onetools.app.live.capsule.CapsuleGestureSlot

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

    /** 高低（纵向）缩放 0.5～2.2，默认 1.05（扁胶囊可再拉高）。 */
    var capsuleHeightScale: Float
        get() = prefs.getFloat(KEY_HEIGHT, legacyScaleOr(1.05f)).coerceIn(0.5f, 2.2f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT, value.coerceIn(0.5f, 2.2f)).apply()

    /** 相对屏幕中心的左右偏移（dp，负左正右） */
    var capsuleOffsetXDp: Int
        get() = prefs.getInt(KEY_OFFSET_X, 0).coerceIn(-120, 120)
        set(value) = prefs.edit().putInt(KEY_OFFSET_X, value.coerceIn(-120, 120)).apply()

    /**
     * 上下偏移（dp）。配合避摄模式微调；CAMERA_CENTER 时相对摄像头中心，BELOW 时相对挖孔底边。
     */
    var capsuleOffsetYDp: Int
        get() = prefs.getInt(KEY_OFFSET_Y, 0).coerceIn(-40, 120)
        set(value) = prefs.edit().putInt(KEY_OFFSET_Y, value.coerceIn(-40, 120)).apply()

    /**
     * 挖孔校准：叠在系统检测到的摄像头中心上（对照 OneCapsule overlayOffset）。
     */
    var cutoutCalibXDp: Int
        get() = prefs.getInt(KEY_CUTOUT_X, 0).coerceIn(-24, 24)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_X, value.coerceIn(-24, 24)).apply()

    var cutoutCalibYDp: Int
        get() = prefs.getInt(KEY_CUTOUT_Y, 0).coerceIn(-16, 16)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_Y, value.coerceIn(-16, 16)).apply()

    /**
     * 避摄策略：默认 BELOW（不挡摄）；CAMERA_CENTER 对齐挖孔（更像 MT 岛）。
     */
    var cameraExclusionMode: String
        // 默认对齐挖孔：一体壳 + 左右文字避摄（海报轻提醒态）。
        get() = prefs.getString(KEY_EXCLUSION, "CAMERA_CENTER") ?: "CAMERA_CENTER"
        set(value) = prefs.edit().putString(
            KEY_EXCLUSION,
            if (value == "BELOW") "BELOW" else "CAMERA_CENTER",
        ).apply()

    /** Material You / 壁纸动态色。 */
    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    /** 展开 / 切会话轻触反馈。 */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    fun gestureAction(slot: CapsuleGestureSlot): CapsuleGestureAction =
        CapsuleGestureAction.fromPref(
            prefs.getString(slot.prefKey, null),
            CapsuleGestureDefaults.actionFor(slot),
        )

    fun setGestureAction(slot: CapsuleGestureSlot, action: CapsuleGestureAction) {
        prefs.edit().putString(slot.prefKey, action.name).apply()
    }

    fun resetCutoutCalibration() {
        prefs.edit()
            .putInt(KEY_CUTOUT_X, 0)
            .putInt(KEY_CUTOUT_Y, 0)
            .apply()
    }

    /** 是否已做过一次系统挖孔自动识别（装后首次 / 手动重识别）。 */
    var cutoutAutoDetected: Boolean
        get() = prefs.getBoolean(KEY_CUTOUT_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_CUTOUT_AUTO, value).apply()

    var lastCutoutCenterX: Int
        get() = prefs.getInt(KEY_CUTOUT_CX, 0)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_CX, value).apply()

    var lastCutoutCenterY: Int
        get() = prefs.getInt(KEY_CUTOUT_CY, 0)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_CY, value).apply()

    var lastCutoutWidth: Int
        get() = prefs.getInt(KEY_CUTOUT_W, 0)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_W, value).apply()

    var lastCutoutHeight: Int
        get() = prefs.getInt(KEY_CUTOUT_H, 0)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_H, value).apply()

    fun saveDetectedCutout(anchor: CameraAnchor) {
        prefs.edit()
            .putBoolean(KEY_CUTOUT_AUTO, true)
            .putInt(KEY_CUTOUT_CX, anchor.centerX)
            .putInt(KEY_CUTOUT_CY, anchor.centerY)
            .putInt(KEY_CUTOUT_W, anchor.width)
            .putInt(KEY_CUTOUT_H, anchor.height)
            .putInt(KEY_CUTOUT_X, 0)
            .putInt(KEY_CUTOUT_Y, 0)
            .apply()
    }

    fun resetGesturesToDefaults() {
        val editor = prefs.edit()
        CapsuleGestureSlot.entries.forEach { slot ->
            editor.putString(slot.prefKey, CapsuleGestureDefaults.actionFor(slot).name)
        }
        editor.apply()
    }

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
        private const val KEY_CUTOUT_X = "cutout_calib_x"
        private const val KEY_CUTOUT_Y = "cutout_calib_y"
        private const val KEY_CUTOUT_AUTO = "cutout_auto_detected"
        private const val KEY_CUTOUT_CX = "cutout_last_cx"
        private const val KEY_CUTOUT_CY = "cutout_last_cy"
        private const val KEY_CUTOUT_W = "cutout_last_w"
        private const val KEY_CUTOUT_H = "cutout_last_h"
        private const val KEY_EXCLUSION = "camera_exclusion_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_HAPTIC = "haptic_enabled"
    }
}

data class LiveStatusPrefsSnapshot(
    val masterEnabled: Boolean,
    val capsuleEnabled: Boolean,
    val enabledSources: Set<LiveStatusSource>,
)
