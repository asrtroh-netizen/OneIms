package com.oneims.app.onekuku

import android.content.Context
import android.util.Log
import com.oneims.app.R
import com.oneims.app.core.ConfigStore
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
        val startedAt = System.currentTimeMillis()
        val restoreId = OneKukuRestoreHistoryStore.newRestoreId()
        val statusBefore = OneKukuRestoreHistoryStore.statusLabel(
            OneKukuHiddenRunner.currentState(),
        )
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        OneKukuHiddenRunner.markExecuting()

        val sim = sims.firstOrNull { it.subscriptionId == selectedSubId }
        if (sim == null || selectedSubId < 0) {
            OneKukuSleepController.sleep()
            val msg = context.getString(R.string.onekuku_restore_need_sim)
            persistHistory(
                context = context,
                restoreId = restoreId,
                startedAt = startedAt,
                sim = null,
                selectedSubId = selectedSubId,
                statusBefore = statusBefore,
                outcome = OneKukuHomeTools.RestoreOutcome.FAILURE,
                detail = emptyMap(),
                snapshot = null,
                userMessage = msg,
            )
            return Report(OneKukuHomeTools.RestoreOutcome.FAILURE, msg)
        }

        val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, selectedSubId, sims)
        val snapshot = (resolved as? SnapshotMatchResult.Matched)?.snapshot
        when (resolved) {
            is SnapshotMatchResult.NoSnapshot -> {
                OneKukuSleepController.sleep()
                val msg = context.getString(R.string.onekuku_restore_no_snapshot)
                persistHistory(
                    context, restoreId, startedAt, sim, selectedSubId, statusBefore,
                    OneKukuHomeTools.RestoreOutcome.FAILURE, emptyMap(), null, msg,
                )
                return Report(OneKukuHomeTools.RestoreOutcome.FAILURE, msg)
            }
            is SnapshotMatchResult.NoMatchingSim -> {
                OneKukuSleepController.sleep()
                val msg = context.getString(R.string.onekuku_restore_sim_mismatch)
                persistHistory(
                    context, restoreId, startedAt, sim, selectedSubId, statusBefore,
                    OneKukuHomeTools.RestoreOutcome.FAILURE, emptyMap(), null, msg,
                )
                return Report(OneKukuHomeTools.RestoreOutcome.FAILURE, msg)
            }
            is SnapshotMatchResult.Matched -> Unit
        }

        if (!ensureOneKukuReady(context)) {
            OneKukuHiddenRunner.markFailed("OneKuku inactive")
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
            OneKukuSleepController.sleep()
            val msg = context.getString(R.string.onekuku_restore_onekuku_inactive)
            persistHistory(
                context, restoreId, startedAt, sim, selectedSubId, statusBefore,
                OneKukuHomeTools.RestoreOutcome.FAILURE, emptyMap(), snapshot, msg,
            )
            return Report(OneKukuHomeTools.RestoreOutcome.FAILURE, msg)
        }

        val result = OneKukuCommandDispatcher.dispatch(
            context = context,
            command = OneKukuCommand.RESTORE_ALL_CALL_CONFIGS,
            subId = selectedSubId,
        )
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

        persistHistory(
            context = context,
            restoreId = restoreId,
            startedAt = startedAt,
            sim = sim,
            selectedSubId = selectedSubId,
            statusBefore = statusBefore,
            outcome = outcome,
            detail = result.detail,
            snapshot = snapshot,
            userMessage = userMessage,
        )
        Log.i(TAG, "manual restore outcome=$outcome detail=$detailOk/$detailTotal")
        return Report(outcome, userMessage, result.detail)
    }

    private fun persistHistory(
        context: Context,
        restoreId: String,
        startedAt: Long,
        sim: SimInfo?,
        selectedSubId: Int,
        statusBefore: String,
        outcome: OneKukuHomeTools.RestoreOutcome,
        detail: Map<String, Boolean>,
        snapshot: OneKukuSnapshot?,
        userMessage: String,
    ) {
        val finishedAt = System.currentTimeMillis()
        val statusAfter = OneKukuRestoreHistoryStore.statusLabel(
            OneKukuHiddenRunner.currentState(),
        )
        val historyResult = when (outcome) {
            OneKukuHomeTools.RestoreOutcome.SUCCESS -> RestoreHistoryResult.SUCCESS
            OneKukuHomeTools.RestoreOutcome.PARTIAL -> RestoreHistoryResult.PARTIAL_SUCCESS
            OneKukuHomeTools.RestoreOutcome.FAILURE -> RestoreHistoryResult.FAILED
        }
        val iccidHash = snapshot?.iccidHash
            ?: OneKukuSnapshotStore.hashIccid(
                OneKukuSnapshotStore.readIccidRaw(context, selectedSubId),
            )
        OneKukuRestoreHistoryStore.save(
            context,
            OneKukuRestoreHistoryRecord(
                restoreId = restoreId,
                startedAt = startedAt,
                finishedAt = finishedAt,
                targetSubId = selectedSubId,
                targetSlotIndex = sim?.slotIndex ?: snapshot?.slotIndex ?: -1,
                carrierName = sim?.carrierName?.ifBlank { null }
                    ?: snapshot?.carrierName.orEmpty(),
                mccmnc = sim?.let { "${it.mcc}${it.mnc}" } ?: snapshot?.mccmnc.orEmpty(),
                iccidHashMasked = iccidHash?.let { OneKukuSnapshotStore.maskHash(it) },
                result = historyResult,
                oneKukuStatusBefore = statusBefore,
                oneKukuStatusAfter = statusAfter,
                itemResults = OneKukuRestoreHistoryStore.mapItemResults(detail, snapshot),
                failureReason = userMessage.takeIf {
                    outcome == OneKukuHomeTools.RestoreOutcome.FAILURE
                },
                logSummary = "restoreId=$restoreId result=$historyResult",
            ),
        )
        ConfigStore.saveReapplyStatus(
            context,
            ConfigStore.ReapplyStatus(
                timestampMillis = finishedAt,
                success = outcome != OneKukuHomeTools.RestoreOutcome.FAILURE,
                trigger = ReapplyTrigger.MANUAL,
                message = userMessage,
            ),
        )
    }

    private fun ensureOneKukuReady(context: Context): Boolean {
        if (OneKukuManager.isReady()) return true
        val wake = OneKukuHiddenRunner.wake()
        if (wake.success && OneKukuManager.isReady()) return true
        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
            OneKukuManager.requestActivation()
            return OneKukuManager.isReady()
        }
        return false
    }
}
