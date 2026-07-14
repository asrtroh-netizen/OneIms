package com.oneims.app.ui

import android.content.Context
import com.oneims.app.R
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.VoWifiNameFormatManager
import java.text.DateFormat
import java.util.Date

/**
 * 首页 OneKuku 工具入口的只读摘要构建；不执行写入，并对用户隐藏底层通道名称。
 */
object OneKukuHomeTools {

    data class SnapshotLine(val label: String, val value: String)

    fun hasConfigSnapshot(context: Context, subId: Int): Boolean {
        if (subId < 0) return false
        return ConfigStore.capabilityUiState(context, subId) != null ||
            ConfigStore.lastApplied(context)?.subId == subId
    }

    fun buildSnapshotLines(context: Context, subId: Int): List<SnapshotLine>? {
        if (subId < 0) return null
        val caps = ConfigStore.capabilityUiState(context, subId)
            ?: ConfigStore.lastApplied(context)?.takeIf { it.subId == subId }?.let {
                ConfigStore.CapabilityUiState(
                    volte = it.volte,
                    vowifi = it.vowifi,
                    vonr = it.vonr,
                    vilte = false,
                    ut = false,
                    crossSim = false,
                    nr5g = false,
                    wfcMode = it.wfcMode,
                )
            }
            ?: return null

        val fiveG = ConfigStore.fiveGDisplayConfig(context)
        val signalAdj = ConfigStore.signalStrengthAdjustmentEnabled(context, subId)
        val vowifiSel = VoWifiNameFormatManager.readSelection(context, subId)
        val identity = ConfigStore.identityDraft(context, subId)
        val on = context.getString(R.string.onekuku_value_on)
        val off = context.getString(R.string.onekuku_value_off)

        val imsCore = buildString {
            append(context.getString(R.string.cap_volte))
            append(if (caps.volte) on else off)
            append(" · ")
            append(context.getString(R.string.cap_vowifi))
            append(if (caps.vowifi) on else off)
            append(" · ")
            append(context.getString(R.string.cap_vonr))
            append(if (caps.vonr) on else off)
        }

        val vowifiFormat = when (val index = vowifiSel.formatIndex) {
            null -> context.getString(R.string.onekuku_value_unset)
            else -> VoWifiNameFormatManager.preview(
                formatIndex = index,
                systemCarrierName = "",
                customCarrierName = vowifiSel.customCarrierName,
            ).ifBlank { context.getString(R.string.onekuku_value_unset) }
        }

        val identityText = when {
            identity == null -> context.getString(R.string.onekuku_value_unset)
            identity.carrierName.isBlank() && identity.imsUserAgent.isBlank() ->
                context.getString(R.string.onekuku_value_unset)
            else -> context.getString(R.string.onekuku_value_configured)
        }

        return listOf(
            SnapshotLine(
                label = context.getString(R.string.onekuku_snapshot_ims_core),
                value = imsCore,
            ),
            SnapshotLine(
                label = context.getString(R.string.onekuku_snapshot_wfc),
                value = context.getString(caps.wfcMode.labelRes),
            ),
            SnapshotLine(
                label = context.getString(R.string.onekuku_snapshot_nr5g),
                value = if (caps.nr5g || fiveG.enabled) on else off,
            ),
            SnapshotLine(
                label = context.getString(R.string.onekuku_snapshot_signal),
                value = if (signalAdj) on else off,
            ),
            SnapshotLine(
                label = context.getString(R.string.onekuku_snapshot_vowifi_name),
                value = vowifiFormat,
            ),
            SnapshotLine(
                label = context.getString(R.string.onekuku_snapshot_identity),
                value = identityText,
            ),
        )
    }

    fun sanitizeUserText(raw: String): String =
        raw
            .replace(Regex("(?i)shizuku"), "OneKuku")
            .replace(Regex("(?i)\\badb\\b"), "调试桥")
            .replace(Regex("(?i)termux"), "终端助手")
            .trim()

    fun restoreResultLabel(context: Context, status: ConfigStore.ReapplyStatus): String {
        val msg = status.message
        return when {
            status.success && msg.contains("部分") ->
                context.getString(R.string.onekuku_restore_partial)
            status.success -> context.getString(R.string.onekuku_restore_success)
            else -> context.getString(R.string.onekuku_restore_failed)
        }
    }

    fun formatRestoreTime(timestampMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(timestampMillis))

    fun settingsStatusLabel(
        context: Context,
        state: OneKukuCardState,
        serviceRunning: Boolean,
    ): String = when {
        state == OneKukuCardState.RUNNING ->
            context.getString(R.string.onekuku_pill_running)
        state == OneKukuCardState.INACTIVE && !serviceRunning ->
            context.getString(R.string.onekuku_pill_invalid)
        state == OneKukuCardState.INACTIVE ->
            context.getString(R.string.onekuku_pill_inactive)
        else -> context.getString(R.string.onekuku_pill_sleeping)
    }

    fun classifyRestoreOutcome(
        success: Boolean,
        message: String,
        detail: Map<String, Boolean> = emptyMap(),
    ): RestoreOutcome {
        val hasFailureInDetail = detail.values.any { !it }
        val hasSuccessInDetail = detail.values.any { it }
        return when {
            success && (message.contains("部分") || (hasFailureInDetail && hasSuccessInDetail)) ->
                RestoreOutcome.PARTIAL
            success -> RestoreOutcome.SUCCESS
            else -> RestoreOutcome.FAILURE
        }
    }

    enum class RestoreOutcome {
        SUCCESS,
        PARTIAL,
        FAILURE,
    }
}
