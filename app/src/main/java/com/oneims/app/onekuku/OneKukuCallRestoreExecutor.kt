package com.oneims.app.onekuku

import android.content.Context
import android.util.Log
import com.oneims.app.R
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.ImsController
import com.oneims.app.core.OneKukuManager
import com.oneims.app.core.OneKukuPrivilegeBridgeImpl
import com.oneims.app.core.ReapplyTrigger
import com.oneims.app.model.SimInfo
import com.oneims.app.ui.OneKukuHomeTools

/**
 * 首页「一键恢复通话」完整执行链（无 APN / 切卡 / 飞行 / radio / 终端）。
 */
object OneKukuCallRestoreExecutor {
    private const val TAG = "OneIMS-Restore"

    data class Report(
        val outcome: OneKukuHomeTools.RestoreOutcome,
        val userMessage: String,
        val detail: Map<String, Boolean> = emptyMap(),
    )

    fun execute(
        context: Context,
        selectedSubId: Int,
        sims: List<SimInfo>,
    ): Report {
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        OneKukuHiddenRunner.markExecuting()

        if (selectedSubId < 0 || sims.none { it.subscriptionId == selectedSubId }) {
            OneKukuSleepController.sleep()
            return fail(context.getString(R.string.onekuku_restore_need_sim))
        }

        val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, selectedSubId, sims)
        when (resolved) {
            is SnapshotMatchResult.NoSnapshot -> {
                OneKukuSleepController.sleep()
                return fail(context.getString(R.string.onekuku_restore_no_snapshot))
            }
            is SnapshotMatchResult.NoMatchingSim -> {
                OneKukuSleepController.sleep()
                return fail(context.getString(R.string.onekuku_restore_sim_mismatch))
            }
            is SnapshotMatchResult.Matched -> Unit
        }

        if (!ensureOneKukuReady(context)) {
            OneKukuHiddenRunner.markFailed("OneKuku inactive")
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
            OneKukuSleepController.sleep()
            return fail(context.getString(R.string.onekuku_restore_onekuku_inactive))
        }

        val result = OneKukuCommandDispatcher.dispatch(
            context = context,
            command = OneKukuCommand.RESTORE_ALL_CALL_CONFIGS,
            subId = selectedSubId,
        )
        // Dispatcher/RestoreManager 已 sleep；这里再确保一次
        OneKukuSleepController.sleep()

        val detailOk = result.detail.values.count { it }
        val detailTotal = result.detail.size
        val outcome = when {
            result.message.contains(OneKukuSnapshotStore.MSG_NO_MATCHING_SIM) ->
                OneKukuHomeTools.RestoreOutcome.FAILURE
            result.message.contains("no snapshot", ignoreCase = true) ->
                OneKukuHomeTools.RestoreOutcome.FAILURE
            result.success && detailTotal > 0 && detailOk == detailTotal ->
                OneKukuHomeTools.RestoreOutcome.SUCCESS
            result.success || detailOk > 0 ->
                OneKukuHomeTools.RestoreOutcome.PARTIAL
            else -> OneKukuHomeTools.RestoreOutcome.FAILURE
        }

        val userMessage = when (outcome) {
            OneKukuHomeTools.RestoreOutcome.SUCCESS ->
                context.getString(R.string.onekuku_restore_toast_success)
            OneKukuHomeTools.RestoreOutcome.PARTIAL ->
                context.getString(R.string.onekuku_restore_toast_partial)
            OneKukuHomeTools.RestoreOutcome.FAILURE -> {
                val reason = when {
                    result.message.contains(OneKukuSnapshotStore.MSG_NO_MATCHING_SIM) ->
                        context.getString(R.string.onekuku_restore_sim_mismatch)
                    result.message.contains("no snapshot", ignoreCase = true) ->
                        context.getString(R.string.onekuku_restore_no_snapshot)
                    result.state == OneKukuRunnerState.INACTIVE ->
                        context.getString(R.string.onekuku_restore_onekuku_inactive)
                    else -> OneKukuHomeTools.sanitizeUserText(result.message).ifBlank {
                        context.getString(R.string.onekuku_history_no_reason)
                    }
                }
                context.getString(R.string.onekuku_restore_toast_failed, reason)
            }
        }

        ConfigStore.saveReapplyStatus(
            context,
            ConfigStore.ReapplyStatus(
                timestampMillis = System.currentTimeMillis(),
                success = outcome != OneKukuHomeTools.RestoreOutcome.FAILURE,
                trigger = ReapplyTrigger.MANUAL,
                message = userMessage,
            ),
        )
        Log.i(TAG, "manual restore outcome=$outcome detail=$detailOk/$detailTotal")
        return Report(outcome, userMessage, result.detail)
    }

    private fun ensureOneKukuReady(context: Context): Boolean {
        if (OneKukuManager.isReady()) return true
        val wake = OneKukuHiddenRunner.wake()
        if (wake.success && OneKukuManager.isReady()) return true
        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
            OneKukuManager.requestActivation()
            // 授权是异步的，本次手动恢复不能假成功
            return OneKukuManager.isReady()
        }
        return false
    }

    private fun fail(message: String): Report =
        Report(OneKukuHomeTools.RestoreOutcome.FAILURE, message)
}
