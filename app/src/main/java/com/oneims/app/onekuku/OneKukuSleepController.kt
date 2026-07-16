package com.oneims.app.onekuku

import android.content.Context
import android.util.Log
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.OneKukuResidentService

/**
 * 执行结束后进入休眠；失败只记日志，不阻断主流程。
 *
 * 对齐 Shizuku：特权活在 `onebridge_server`，休眠只停 App 侧 FG/标签；
 * binder 仍在时 [OneKukuHiddenRunner.wake] 为秒级状态翻转。
 */
object OneKukuSleepController {
    private const val TAG = "OneIMS-OneKuku"

    fun sleep(context: Context? = null): OneKukuCommandResult {
        return try {
            OneKukuHiddenRunner.markSleeping()
            context?.let { OneKukuResidentService.stop(it) }
            Log.i(TAG, "sleep ok")
            OneKukuCommandResult(
                success = true,
                state = OneKukuRunnerState.SLEEPING,
                message = "OneKuku sleeping",
            )
        } catch (error: Throwable) {
            Log.w(TAG, "sleep failed: ${error.message}")
            OneKukuCommandResult(
                success = false,
                state = OneKukuHiddenRunner.currentState(),
                message = "sleep failed: ${error.message}",
            )
        }
    }

    fun sleepIfEnabled(context: Context): OneKukuCommandResult {
        return if (ConfigStore.isOneKukuAutoSleep(context)) {
            sleep(context)
        } else {
            Log.i(TAG, "auto-sleep disabled, keep current state")
            OneKukuCommandResult(
                success = true,
                state = OneKukuHiddenRunner.currentState(),
                message = "auto-sleep disabled",
            )
        }
    }
}
