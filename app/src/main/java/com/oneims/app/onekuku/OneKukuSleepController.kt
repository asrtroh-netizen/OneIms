package com.oneims.app.onekuku

import android.content.Context
import android.util.Log

/**
 * 任务收尾：产品改为 OneKuku 常驻，执行结束后回到 ACTIVE，不再进入休眠。
 * [sleep]/[sleepIfEnabled] 保留旧调用点，语义统一为「回到常驻」。
 */
object OneKukuSleepController {
    private const val TAG = "OneIMS-OneKuku"

    fun sleep(): OneKukuCommandResult {
        return try {
            OneKukuHiddenRunner.markActive()
            Log.i(TAG, "resident ok (sleep API kept for callers)")
            OneKukuCommandResult(
                success = true,
                state = OneKukuRunnerState.ACTIVE,
                message = "OneKuku resident",
            )
        } catch (error: Throwable) {
            Log.w(TAG, "resident mark failed: ${error.message}")
            OneKukuCommandResult(
                success = false,
                state = OneKukuHiddenRunner.currentState(),
                message = "resident mark failed: ${error.message}",
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun sleepIfEnabled(context: Context): OneKukuCommandResult {
        // 常驻策略：忽略 autoSleep 偏好，任务后一律回 ACTIVE。
        Log.i(TAG, "auto-sleep ignored; keep OneKuku resident")
        return sleep()
    }
}
