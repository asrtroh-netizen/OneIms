package com.oneims.app.ui

import android.content.Context
import com.oneims.app.R
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.VoWifiNameFormatManager
import com.oneims.app.model.WfcMode
import com.oneims.app.onekuku.OneKukuSnapshot
import com.oneims.app.onekuku.OneKukuSnapshotStore
import com.oneims.app.onekuku.OneKukuRestoreHistoryStore
import com.oneims.app.onekuku.RestoreHistoryResult
import com.oneims.app.onekuku.RestoreItemStatus
import com.oneims.app.onekuku.SnapshotMatchResult
import java.text.DateFormat
import java.util.Date

/**
 * 首页 OneKuku 工具入口的只读摘要构建；不执行写入，并对用户隐藏底层通道名称。
 */
object OneKukuHomeTools {

    data class SnapshotLine(val label: String, val value: String)

    fun buildStatusCheckLines(
        context: Context,
        selectedSubId: Int,
        cardState: OneKukuCardState,
        serviceRunning: Boolean,
        serviceGranted: Boolean,
        sims: List<com.oneims.app.model.SimInfo>,
    ): List<SnapshotLine> {
        val statusLabel = settingsStatusLabel(context, cardState, serviceRunning)
        val simLine = sims.firstOrNull { it.subscriptionId == selectedSubId }?.let {
            context.getString(R.string.onekuku_status_sim, it.slotIndex + 1, it.carrierName)
        } ?: context.getString(R.string.onekuku_status_no_sim)
        val fiveG = ConfigStore.fiveGDisplayConfig(context)
        val fiveGLine = context.getString(
            R.string.onekuku_status_5g,
            if (fiveG.enabled) {
                context.getString(R.string.onekuku_value_on)
            } else {
                context.getString(R.string.onekuku_value_off)
            },
        )
        val imsLine = if (selectedSubId >= 0 && serviceGranted) {
            sanitizeUserText(
                com.oneims.app.core.ImsController.queryImsStatus(context, selectedSubId).rawText,
            )
        } else {
            context.getString(R.string.onekuku_status_ims_skipped)
        }
        return listOf(
            SnapshotLine(
                label = context.getString(R.string.onekuku_tool_status_title),
                value = context.getString(R.string.onekuku_status_onekuku, statusLabel),
            ),
            SnapshotLine(
                label = context.getString(R.string.cap_group_radio_title),
                value = "$simLine\n$fiveGLine",
            ),
            SnapshotLine(
                label = "IMS / VoWiFi",
                value = imsLine,
            ),
            SnapshotLine(
                label = context.getString(R.string.onekuku_subtitle_active),
                value = when {
                    serviceRunning && serviceGranted ->
                        context.getString(R.string.onekuku_settings_state_sleeping)
                    serviceRunning ->
                        context.getString(R.string.onekuku_settings_state_inactive)
                    else ->
                        context.getString(R.string.onekuku_settings_state_invalid)
                },
            ),
        )
    }

    fun hasConfigSnapshot(context: Context, subId: Int): Boolean {
        if (subId < 0) return false
        val sim = com.oneims.app.core.ImsController.listSims(context)
            .firstOrNull { it.subscriptionId == subId }
        if (sim != null && OneKukuSnapshotStore.findForSim(context, sim) != null) {
            return true
        }
        if (OneKukuSnapshotStore.load(context, subId) != null) return true
        return ConfigStore.capabilityUiState(context, subId) != null ||
            ConfigStore.lastApplied(context)?.subId == subId
    }

    fun buildSnapshotLines(context: Context, subId: Int): List<SnapshotLine>? {
        if (subId < 0) return null
        val sims = com.oneims.app.core.ImsController.listSims(context)
        val fromStore = when (
            val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, subId, sims)
        ) {
            is SnapshotMatchResult.Matched -> resolved.snapshot
            else -> OneKukuSnapshotStore.load(context, subId)
        }
        if (fromStore != null) {
            return linesFromSnapshot(context, fromStore)
        }

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

    private fun linesFromSnapshot(context: Context, snap: OneKukuSnapshot): List<SnapshotLine> {
        val on = context.getString(R.string.onekuku_value_on)
        val off = context.getString(R.string.onekuku_value_off)
        fun bool(group: String, key: String, default: Boolean = false): Boolean =
            snap.entries.firstOrNull { it.configGroup == group && it.configKey == key }
                ?.configValue
                ?.toBooleanStrictOrNull()
                ?: default

        val imsCore = buildString {
            append(context.getString(R.string.cap_volte))
            append(if (bool("ims", "volte", true)) on else off)
            append(" · ")
            append(context.getString(R.string.cap_vowifi))
            append(if (bool("ims", "vowifi", true)) on else off)
            append(" · ")
            append(context.getString(R.string.cap_vonr))
            append(if (bool("ims", "vonr", false)) on else off)
        }
        val wfcValue = snap.entries.firstOrNull {
            (it.configGroup == "wfc" && it.configKey == "mode") ||
                (it.configGroup == "ims" && it.configKey == "wfcMode")
        }?.configValue?.toIntOrNull()
        val wfcMode = WfcMode.of(wfcValue ?: 1)
        val formatIndex = snap.entries.firstOrNull {
            it.configGroup == "vowifi_name" && it.configKey == "formatIndex"
        }?.configValue?.toIntOrNull()
        val customCarrier = snap.entries.firstOrNull {
            it.configGroup == "vowifi_name" && it.configKey == "customCarrier"
        }?.configValue.orEmpty()
        val vowifiFormat = when (formatIndex) {
            null -> context.getString(R.string.onekuku_value_unset)
            else -> VoWifiNameFormatManager.preview(
                formatIndex = formatIndex,
                systemCarrierName = "",
                customCarrierName = customCarrier,
            ).ifBlank { context.getString(R.string.onekuku_value_unset) }
        }
        val hasIdentity = snap.entries.any { it.configGroup == "identity" }
        val fiveGDisplay = bool("five_g_display", "enabled", false)

        return buildList {
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_ims_core),
                    value = imsCore,
                ),
            )
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_wfc),
                    value = context.getString(wfcMode.labelRes),
                ),
            )
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_nr5g),
                    value = if (bool("nr5g", "enabled", false)) on else off,
                ),
            )
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_signal),
                    value = if (bool("signal", "adjustment", false)) on else off,
                ),
            )
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_vowifi_name),
                    value = vowifiFormat,
                ),
            )
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_identity),
                    value = if (hasIdentity) {
                        context.getString(R.string.onekuku_value_configured)
                    } else {
                        context.getString(R.string.onekuku_value_unset)
                    },
                ),
            )
            if (fiveGDisplay) {
                add(
                    SnapshotLine(
                        label = context.getString(R.string.onekuku_snapshot_five_g_display),
                        value = on,
                    ),
                )
            }
            add(
                SnapshotLine(
                    label = context.getString(R.string.onekuku_snapshot_meta),
                    value = context.getString(
                        R.string.onekuku_snapshot_meta_value,
                        snap.slotIndex + 1,
                        snap.carrierName.ifBlank { "—" },
                        OneKukuSnapshotStore.maskHash(snap.iccidHash),
                    ),
                ),
            )
        }
    }

    fun buildRestoreHistoryLines(context: Context): List<SnapshotLine>? {
        val record = OneKukuRestoreHistoryStore.loadLatest(context) ?: return null
        val relative = formatRelativeRestoreTime(context, record.finishedAt)
        val resultLabel = when (record.result) {
            RestoreHistoryResult.SUCCESS ->
                context.getString(R.string.onekuku_history_result_success)
            RestoreHistoryResult.PARTIAL_SUCCESS ->
                context.getString(R.string.onekuku_history_result_partial)
            RestoreHistoryResult.FAILED ->
                context.getString(R.string.onekuku_history_result_failed)
        }
        val oneKukuLabel = when (record.oneKukuStatusAfter) {
            "sleeping" -> context.getString(R.string.onekuku_history_kuku_sleeping)
            "failed" -> context.getString(R.string.onekuku_history_kuku_failed)
            "inactive" -> context.getString(R.string.onekuku_history_kuku_inactive)
            else -> context.getString(R.string.onekuku_history_kuku_failed)
        }
        val slot = (record.targetSlotIndex + 1).coerceAtLeast(1)
        val carrier = record.carrierName.ifBlank { "—" }
        val target = context.getString(R.string.onekuku_history_target_value, slot, carrier)

        fun itemLabel(key: String): String = when (key) {
            "identity" -> context.getString(R.string.onekuku_history_item_identity)
            "ims" -> context.getString(R.string.onekuku_history_item_ims)
            "wfc" -> context.getString(R.string.onekuku_history_item_wfc)
            "nr5g" -> context.getString(R.string.onekuku_history_item_nr5g)
            "signal" -> context.getString(R.string.onekuku_history_item_signal)
            "vowifi_name" -> context.getString(R.string.onekuku_history_item_vowifi)
            else -> key
        }

        fun itemStatus(status: RestoreItemStatus): String = when (status) {
            RestoreItemStatus.SUCCESS -> context.getString(R.string.onekuku_history_item_ok)
            RestoreItemStatus.FAILED -> context.getString(R.string.onekuku_history_item_fail)
            RestoreItemStatus.SKIPPED -> context.getString(R.string.onekuku_history_item_skip)
        }

        return buildList {
            add(
                SnapshotLine(
                    context.getString(R.string.onekuku_history_recent),
                    relative,
                ),
            )
            add(
                SnapshotLine(
                    context.getString(R.string.onekuku_history_result_label),
                    resultLabel,
                ),
            )
            add(
                SnapshotLine(
                    context.getString(R.string.onekuku_history_onekuku_label),
                    oneKukuLabel,
                ),
            )
            add(
                SnapshotLine(
                    context.getString(R.string.onekuku_history_target_label),
                    target,
                ),
            )
            record.itemResults.forEach { (key, status) ->
                add(SnapshotLine(itemLabel(key), itemStatus(status)))
            }
            if (record.result == RestoreHistoryResult.FAILED) {
                val reason = record.failureReason
                    ?.let { sanitizeUserText(it) }
                    ?.ifBlank { null }
                    ?: context.getString(R.string.onekuku_history_no_reason)
                add(
                    SnapshotLine(
                        context.getString(R.string.onekuku_history_reason_label),
                        reason,
                    ),
                )
            }
        }
    }

    fun formatRelativeRestoreTime(context: Context, timestampMillis: Long): String {
        val delta = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
        val minutes = delta / 60_000L
        val hours = delta / 3_600_000L
        return when {
            minutes < 1L -> context.getString(R.string.onekuku_history_just_now)
            minutes < 60L ->
                context.getString(R.string.onekuku_history_minutes_ago, minutes.toInt())
            hours < 48L ->
                context.getString(R.string.onekuku_history_hours_ago, hours.toInt())
            else -> formatRestoreTime(timestampMillis)
        }
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
        state == OneKukuCardState.EXECUTING ->
            context.getString(R.string.onekuku_settings_state_running)
        state == OneKukuCardState.FAILED ->
            context.getString(R.string.onekuku_settings_state_inactive)
        state == OneKukuCardState.INACTIVE && !serviceRunning ->
            context.getString(R.string.onekuku_settings_state_invalid)
        state == OneKukuCardState.INACTIVE ||
            state == OneKukuCardState.WAITING_PAIR ->
            context.getString(R.string.onekuku_settings_state_inactive)
        else -> context.getString(R.string.onekuku_settings_state_sleeping)
    }

    fun classifyRestoreOutcome(
        success: Boolean,
        message: String,
        detail: Map<String, Boolean> = emptyMap(),
    ): RestoreOutcome {
        val hasFailureInDetail = detail.values.any { !it }
        val hasSuccessInDetail = detail.values.any { it }
        return when {
            message.contains(OneKukuSnapshotStore.MSG_NO_MATCHING_SIM) ->
                RestoreOutcome.FAILURE
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
