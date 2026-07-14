package com.oneims.app.onekuku

import android.content.Context
import android.util.Log
import com.oneims.app.core.ImsController
import com.oneims.app.core.SystemDisplayOverrideManager
import com.oneims.app.core.VoWifiNameFormatManager
import com.oneims.app.model.ConfigResult
import com.oneims.app.model.WfcMode

/**
 * 按快照恢复通话配置：单项失败不阻断后续，末尾汇总。
 * 顺序：身份 → IMS → WFC → 5G NR → 信号强度 → VoWiFi 名称。
 */
object OneKukuRestoreManager {
    private const val TAG = "OneIMS-Restore"

    fun restoreAll(context: Context, subId: Int): OneKukuCommandResult {
        OneKukuHiddenRunner.markExecuting()
        val wake = OneKukuHiddenRunner.wake()
        if (!wake.success) {
            OneKukuHiddenRunner.markFailed(wake.message)
            return wake
        }
        val sims = ImsController.listSims(context)
        when (val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, subId, sims)) {
            is SnapshotMatchResult.NoSnapshot -> {
                val msg = "no snapshot"
                Log.w(TAG, msg)
                OneKukuSleepController.sleep()
                return OneKukuCommandResult(false, OneKukuRunnerState.SLEEPING, msg)
            }
            is SnapshotMatchResult.NoMatchingSim -> {
                Log.w(TAG, OneKukuSnapshotStore.MSG_NO_MATCHING_SIM)
                OneKukuSleepController.sleep()
                return OneKukuCommandResult(
                    false,
                    OneKukuRunnerState.SLEEPING,
                    OneKukuSnapshotStore.MSG_NO_MATCHING_SIM,
                )
            }
            is SnapshotMatchResult.Matched -> {
                val snapshot = resolved.snapshot
                val writeSubId = resolved.writeSubId
                // 明确排除 APN：一键恢复通话不自动恢复 APN。
                val detail = linkedMapOf<String, Boolean>()
                detail["identity"] = restoreIdentity(context, writeSubId, snapshot).success
                detail["ims"] = restoreIms(context, writeSubId, snapshot).success
                detail["wfc"] = restoreWfc(context, writeSubId, snapshot).success
                detail["nr5g"] = restoreFiveG(context, writeSubId, snapshot).success
                detail["signal"] = restoreSignal(context, writeSubId, snapshot).success
                detail["vowifi_name"] = restoreVoWifiName(context, writeSubId, snapshot).success
                detail["verify"] = verify(context, writeSubId).success

                val successCount = detail.values.count { it }
                val total = detail.size
                val allOk = successCount == total
                val partial = successCount > 0 && !allOk
                val status = when {
                    allOk -> "success"
                    partial -> "partial"
                    else -> "failed"
                }
                OneKukuSnapshotStore.updateRestoreStatus(
                    context = context,
                    subId = writeSubId,
                    status = status,
                    verifiedAt = System.currentTimeMillis(),
                )
                OneKukuSleepController.sleep()
                val message = "restore $status ($successCount/$total)"
                Log.i(TAG, message)
                return OneKukuCommandResult(
                    success = allOk || partial,
                    state = OneKukuRunnerState.SLEEPING,
                    message = message,
                    detail = detail,
                )
            }
        }
    }

    fun restoreIms(context: Context, subId: Int): OneKukuCommandResult =
        step("ims") { restoreIms(context, subId, requireSnapshot(context, subId)) }

    fun restoreWfc(context: Context, subId: Int): OneKukuCommandResult =
        step("wfc") { restoreWfc(context, subId, requireSnapshot(context, subId)) }

    fun restoreFiveG(context: Context, subId: Int): OneKukuCommandResult =
        step("nr5g") { restoreFiveG(context, subId, requireSnapshot(context, subId)) }

    fun restoreVoWifiName(context: Context, subId: Int): OneKukuCommandResult =
        step("vowifi_name") { restoreVoWifiName(context, subId, requireSnapshot(context, subId)) }

    fun restoreIdentity(context: Context, subId: Int): OneKukuCommandResult =
        step("identity") { restoreIdentity(context, subId, requireSnapshot(context, subId)) }

    fun verify(context: Context, subId: Int): OneKukuCommandResult {
        val ok = runCatching {
            ImsController.queryImsStatus(context, subId)
            true
        }.getOrDefault(false)
        return OneKukuCommandResult(
            success = ok,
            state = OneKukuHiddenRunner.currentState(),
            message = if (ok) "verify ok" else "verify failed",
            detail = mapOf("verify" to ok),
        )
    }

    private fun step(
        name: String,
        block: () -> ConfigResult,
    ): OneKukuCommandResult {
        OneKukuHiddenRunner.markExecuting()
        val wake = OneKukuHiddenRunner.wake()
        if (!wake.success) return wake
        val result = runCatching(block).getOrElse {
            ConfigResult(false, it.message ?: "error")
        }
        Log.i(TAG, "step=$name success=${result.success} msg=${result.message}")
        OneKukuSleepController.sleep()
        return OneKukuCommandResult(
            success = result.success,
            state = OneKukuRunnerState.SLEEPING,
            message = result.message,
            detail = mapOf(name to result.success),
        )
    }

    private fun requireSnapshot(context: Context, subId: Int): OneKukuSnapshot {
        val sims = ImsController.listSims(context)
        return when (val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, subId, sims)) {
            is SnapshotMatchResult.Matched -> resolved.snapshot
            is SnapshotMatchResult.NoMatchingSim ->
                error(OneKukuSnapshotStore.MSG_NO_MATCHING_SIM)
            is SnapshotMatchResult.NoSnapshot ->
                error("snapshot missing")
        }
    }

    private fun restoreIdentity(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val carrier = snapshot.entry("identity", "carrierName") ?: return ConfigResult(true, "skip")
        val ua = snapshot.entry("identity", "imsUserAgent").orEmpty()
        if (carrier.isBlank() && ua.isBlank()) return ConfigResult(true, "skip")
        // UA 可能已打码；打码值跳过以免写坏
        val safeUa = if (ua.contains('…') || ua == "***") "" else ua
        return ImsController.applyIdentityOverride(context, subId, carrier, safeUa)
    }

    private fun restoreIms(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val volte = snapshot.bool("ims", "volte", true)
        val vowifi = snapshot.bool("ims", "vowifi", true)
        val vonr = snapshot.bool("ims", "vonr", false)
        val wfc = WfcMode.of(
            snapshot.entry("wfc", "mode")?.toIntOrNull()
                ?: snapshot.entry("ims", "wfcMode")?.toIntOrNull()
                ?: 1,
        )
        return ImsController.applyAll(context, subId, volte, vowifi, vonr, wfc)
    }

    private fun restoreWfc(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val wfc = WfcMode.of(
            snapshot.entry("wfc", "mode")?.toIntOrNull()
                ?: snapshot.entry("ims", "wfcMode")?.toIntOrNull()
                ?: 1,
        )
        return ImsController.setWfcMode(context, subId, wfc)
    }

    private fun restoreFiveG(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val enabled = snapshot.bool("nr5g", "enabled", false)
        if (!enabled) return ConfigResult(true, "skip")
        return ImsController.apply5g(context, subId, true)
    }

    private fun restoreSignal(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val enabled = snapshot.bool("signal", "adjustment", false)
        return runCatching {
            val message = SystemDisplayOverrideManager.applySignalStrengthAdjustment(
                context = context,
                subId = subId,
                enabled = enabled,
                preferenceEnabled = enabled,
            )
            ConfigResult(true, message)
        }.getOrElse { ConfigResult(false, it.message ?: "signal failed") }
    }

    private fun restoreVoWifiName(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val index = snapshot.entry("vowifi_name", "formatIndex")?.toIntOrNull()
            ?: return ConfigResult(true, "skip")
        val custom = snapshot.entry("vowifi_name", "customCarrier").orEmpty()
        return runCatching {
            val message = VoWifiNameFormatManager.apply(
                context = context,
                subId = subId,
                formatIndex = index,
                customCarrierName = custom,
            )
            ConfigResult(true, message)
        }.getOrElse { ConfigResult(false, it.message ?: "vowifi name failed") }
    }

    private fun OneKukuSnapshot.entry(type: String, key: String): String? =
        entries.firstOrNull { it.configGroup == type && it.configKey == key }?.configValue

    private fun OneKukuSnapshot.bool(type: String, key: String, default: Boolean): Boolean =
        entry(type, key)?.toBooleanStrictOrNull() ?: default
}
