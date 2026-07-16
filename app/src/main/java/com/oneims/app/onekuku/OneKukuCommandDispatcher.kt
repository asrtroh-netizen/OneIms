package com.oneims.app.onekuku

import android.content.Context
import android.util.Log

/**
 * 白名单命令分发：禁止非白名单与通用 shell。
 */
object OneKukuCommandDispatcher {
    private const val TAG = "OneIMS-OneKuku"

    fun dispatch(
        context: Context,
        command: OneKukuCommand,
        subId: Int,
    ): OneKukuCommandResult {
        Log.i(TAG, "dispatch command=$command subId=$subId")
        return when (command) {
            OneKukuCommand.ACTIVATE_ONEKUKU -> OneKukuHiddenRunner.wake()
            OneKukuCommand.CHECK_ONEKUKU_STATUS -> {
                OneKukuHiddenRunner.refreshFromBridge()
                val state = OneKukuHiddenRunner.currentState()
                OneKukuCommandResult(
                    success = state != OneKukuRunnerState.FAILED &&
                        state != OneKukuRunnerState.INACTIVE,
                    state = state,
                    message = "OneKuku state=$state",
                )
            }
            OneKukuCommand.SLEEP_ONEKUKU -> OneKukuSleepController.sleep(context)
            OneKukuCommand.RESTORE_ALL_CALL_CONFIGS ->
                OneKukuRestoreManager.restoreAll(context, subId)
            OneKukuCommand.RESTORE_IMS ->
                OneKukuRestoreManager.restoreIms(context, subId)
            OneKukuCommand.RESTORE_WFC ->
                OneKukuRestoreManager.restoreWfc(context, subId)
            OneKukuCommand.RESTORE_5G ->
                OneKukuRestoreManager.restoreFiveG(context, subId)
            OneKukuCommand.RESTORE_VOWIFI_NAME ->
                OneKukuRestoreManager.restoreVoWifiName(context, subId)
            OneKukuCommand.RESTORE_IDENTITY_OVERRIDE ->
                OneKukuRestoreManager.restoreIdentity(context, subId)
            OneKukuCommand.VERIFY_CURRENT_CONFIG ->
                OneKukuRestoreManager.verify(context, subId)
        }
    }
}
