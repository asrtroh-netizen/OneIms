package com.oneims.app.onekuku

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * 开机自动检查/恢复的首页状态提示与「同开机仅一次」标记。
 */
enum class OneKukuBootUiHint {
    /** 配置正常或无需打扰：休眠。 */
    READY_SLEEPING,

    /** 暂无快照：休眠，但可提示无快照。 */
    NO_SNAPSHOT_SLEEPING,

    /** 正在自动恢复。 */
    RESTORING,

    /** 自动恢复成功。 */
    RESTORE_COMPLETE,

    /** OneKuku 不可用或自动失败：未激活红卡。 */
    NEEDS_ACTIVATION,
}

object OneKukuBootRestoreStore {
    private const val TAG = "OneIMS-OneKuku"
    private const val PREF = "onekuku_boot_restore"
    private const val KEY_HINT = "ui_hint"
    private const val KEY_ATTEMPTED_BOOT = "attempted_boot_id"
    private const val KEY_NO_SNAPSHOT_NOTE = "no_snapshot_note"

    fun readHint(context: Context): OneKukuBootUiHint {
        val raw = prefs(context).getString(KEY_HINT, OneKukuBootUiHint.READY_SLEEPING.name)
        return runCatching { OneKukuBootUiHint.valueOf(raw!!) }
            .getOrDefault(OneKukuBootUiHint.READY_SLEEPING)
    }

    fun writeHint(context: Context, hint: OneKukuBootUiHint) {
        prefs(context).edit().putString(KEY_HINT, hint.name).apply()
        Log.i(TAG, "boot ui hint=$hint")
    }

    fun shouldShowNoSnapshotNote(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NO_SNAPSHOT_NOTE, false)

    fun setNoSnapshotNote(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_NO_SNAPSHOT_NOTE, show).apply()
    }

    /** 同一次开机最多自动恢复 1 次。 */
    fun hasAttemptedThisBoot(context: Context): Boolean {
        val bootId = currentBootId(context)
        return prefs(context).getString(KEY_ATTEMPTED_BOOT, null) == bootId
    }

    fun markAttemptedThisBoot(context: Context) {
        prefs(context).edit().putString(KEY_ATTEMPTED_BOOT, currentBootId(context)).apply()
    }

    @SuppressLint("HardwareIds")
    fun currentBootId(context: Context): String {
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, "boot_count")
        }.getOrDefault(-1)
        return if (bootCount >= 0) {
            "bc_$bootCount"
        } else {
            // fallback：用进程启动时 elapsed 粗分代（同开机内稳定）
            "er_${SystemClock.elapsedRealtime() / 86_400_000L}"
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
