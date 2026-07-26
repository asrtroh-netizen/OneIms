package com.onetools.app.special.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.onetools.app.R
import com.onetools.app.special.display.FiveGDisplayController
import com.onetools.app.special.display.SignalBarController
import com.onetools.app.special.display.SpecialFeatureStore
import com.onetools.app.special.privilege.SpecialPrivilege
import com.onetools.app.special.sim.DataSimController
import com.onetools.app.special.sim.SpecialSimInfo
import com.onetools.app.special.sim.SpecialSimSwitchResult
import com.onetools.app.special.sim.TileHelper
import com.onetools.app.ui.OneToolsGroupDivider
import com.onetools.app.ui.OneToolsInlineNotice
import com.onetools.app.ui.OneToolsPrimaryButton
import com.onetools.app.ui.OneToolsSection
import com.onetools.app.ui.OneToolsSelectedSimPill
import com.onetools.app.ui.OneToolsSettingsActionRow
import com.onetools.app.ui.OneToolsSettingsChoiceRow
import com.onetools.app.ui.OneToolsSettingsGroup
import com.onetools.app.ui.OneToolsSettingsSwitchRow
import com.onetools.app.ui.OneToolsToolPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 特色功能页：UI 结构对齐 OneIMS 独家页三项（信号格 / 5G 显示 / 控制中心切卡）。
 */
@Composable
fun SpecialFeaturesScreen(
    onBack: () -> Unit,
    channelReady: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubId by remember { mutableStateOf(-1) }
    var sims by remember { mutableStateOf<List<SpecialSimInfo>>(emptyList()) }
    var signalMode by remember { mutableStateOf(SpecialFeatureStore.SignalBarMode.AUTO) }
    var fiveG by remember { mutableStateOf(SpecialFeatureStore.fiveGConfig(context)) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingSwitchSim by remember { mutableStateOf<SpecialSimInfo?>(null) }
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val privilegeReady = channelReady && SpecialPrivilege.isReady()
    val actionsEnabled = !busy && privilegeReady && hasPhonePermission
    val targetSim = sims.firstOrNull { it.subId == selectedSubId }
    val applySignalLabel = stringResource(R.string.special_signal_apply)
    val applyFiveGLabel = stringResource(R.string.special_five_g_apply)

    fun refreshSims() {
        hasPhonePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        sims = if (hasPhonePermission) {
            runCatching { DataSimController.getActiveSims(context) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (sims.none { it.subId == selectedSubId }) {
            selectedSubId = sims.firstOrNull { it.isDefaultData }?.subId
                ?: sims.firstOrNull()?.subId
                ?: SubscriptionManager.getDefaultDataSubscriptionId().takeIf { it >= 0 }
                ?: -1
        }
        if (selectedSubId >= 0) {
            signalMode = SpecialFeatureStore.signalBarMode(context, selectedSubId)
        }
        fiveG = SpecialFeatureStore.fiveGConfig(context)
    }

    fun selectSim(subId: Int) {
        selectedSubId = subId
        if (subId >= 0) {
            signalMode = SpecialFeatureStore.signalBarMode(context, subId)
        }
    }

    fun runSwitch(sim: SpecialSimInfo) {
        busy = true
        busyLabel = context.getString(R.string.special_qs_switching)
        scope.launch {
            val result = DataSimController.switchDefaultDataSubId(context, sim.subId)
            val msg = when (result) {
                is SpecialSimSwitchResult.Success ->
                    result.warning
                        ?: context.getString(
                            R.string.special_data_switch_success,
                            sim.slotIndex + 1,
                        )
                is SpecialSimSwitchResult.Failed ->
                    context.getString(R.string.special_data_switch_failed, result.reason)
            }
            status = msg
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            refreshSims()
            busy = false
            busyLabel = null
        }
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPhonePermission = granted
        refreshSims()
        status = if (granted) {
            context.getString(R.string.special_phone_permission_granted)
        } else {
            context.getString(R.string.special_phone_permission_denied)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPhonePermission) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        } else {
            refreshSims()
        }
    }

    pendingSwitchSim?.let { sim ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingSwitchSim = null },
            title = {
                Text(
                    stringResource(
                        R.string.special_data_switch_confirm_title,
                        sim.slotIndex + 1,
                        sim.shortName,
                    ),
                )
            },
            text = { Text(stringResource(R.string.special_data_switch_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSwitchSim = null
                        runSwitch(sim)
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.special_data_switch_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingSwitchSim = null },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.special_data_switch_cancel))
                }
            },
        )
    }

    OneToolsToolPage(
        title = stringResource(R.string.special_title),
        subtitle = stringResource(R.string.special_subtitle),
        onBack = onBack,
        verticalSpacing = 20,
    ) {
        item {
            OneToolsInlineNotice(
                text = when {
                    !privilegeReady -> stringResource(R.string.special_channel_need)
                    !hasPhonePermission -> stringResource(R.string.special_phone_permission_need)
                    else -> stringResource(R.string.special_channel_ready)
                },
                danger = !privilegeReady || !hasPhonePermission,
            )
        }

        if (!hasPhonePermission) {
            item {
                OneToolsSettingsGroup {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OneToolsPrimaryButton(
                            text = stringResource(R.string.special_phone_permission_action),
                            onClick = {
                                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            },
                        )
                    }
                }
            }
        }

        if (sims.isNotEmpty()) {
            item {
                OneToolsSection(title = stringResource(R.string.special_target_preview_section)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OneToolsSelectedSimPill(
                            labels = sims.map { sim ->
                                sim.subId to context.getString(
                                    R.string.special_sim_pill_label,
                                    sim.slotIndex + 1,
                                    sim.shortName,
                                )
                            },
                            selectedSubId = selectedSubId,
                            onSelectSim = ::selectSim,
                            enabled = !busy && hasPhonePermission,
                        )
                    }
                }
            }
        }

        item {
            OneToolsSection(title = stringResource(R.string.special_signal_title)) {
                if (targetSim != null) {
                    Text(
                        text = stringResource(
                            R.string.special_target_preview,
                            targetSim.slotIndex + 1,
                            targetSim.shortName,
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OneToolsGroupDivider()
                }
                OneToolsSettingsChoiceRow(
                    title = stringResource(R.string.special_signal_auto),
                    subtitle = stringResource(R.string.special_signal_auto_sub),
                    selected = signalMode == SpecialFeatureStore.SignalBarMode.AUTO,
                    enabled = !busy,
                    onClick = { signalMode = SpecialFeatureStore.SignalBarMode.AUTO },
                )
                OneToolsGroupDivider()
                OneToolsSettingsChoiceRow(
                    title = stringResource(R.string.special_signal_four),
                    subtitle = stringResource(R.string.special_signal_four_sub),
                    selected = signalMode == SpecialFeatureStore.SignalBarMode.FOUR_BARS,
                    enabled = !busy,
                    onClick = { signalMode = SpecialFeatureStore.SignalBarMode.FOUR_BARS },
                )
                OneToolsGroupDivider()
                OneToolsSettingsChoiceRow(
                    title = stringResource(R.string.special_signal_five),
                    subtitle = stringResource(R.string.special_signal_five_sub),
                    selected = signalMode == SpecialFeatureStore.SignalBarMode.FIVE_BARS,
                    enabled = !busy,
                    onClick = { signalMode = SpecialFeatureStore.SignalBarMode.FIVE_BARS },
                )
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OneToolsPrimaryButton(
                        text = applySignalLabel,
                        onClick = {
                            if (selectedSubId < 0) {
                                status = context.getString(R.string.special_data_switch_no_sim)
                                return@OneToolsPrimaryButton
                            }
                            busy = true
                            busyLabel = applySignalLabel
                            scope.launch {
                                val msg = withContext(Dispatchers.IO) {
                                    runCatching {
                                        SignalBarController.apply(
                                            context,
                                            selectedSubId,
                                            signalMode,
                                        )
                                    }.getOrElse { it.message ?: it.toString() }
                                }
                                status = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                busy = false
                                busyLabel = null
                            }
                        },
                        enabled = actionsEnabled && selectedSubId >= 0,
                        loading = busyLabel == applySignalLabel,
                    )
                }
            }
        }

        item {
            OneToolsSection(title = stringResource(R.string.special_five_g_title)) {
                if (targetSim != null) {
                    Text(
                        text = stringResource(
                            R.string.special_target_preview,
                            targetSim.slotIndex + 1,
                            targetSim.shortName,
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OneToolsGroupDivider()
                }
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.special_five_g_enable),
                    subtitle = stringResource(R.string.special_five_g_enable_sub),
                    checked = fiveG.enabled,
                    onCheckedChange = { fiveG = fiveG.copy(enabled = it) },
                    enabled = !busy,
                    icon = Icons.Filled.Star,
                )
                if (fiveG.enabled) {
                    OneToolsGroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        OneToolsSettingsChoiceRow(
                            title = stringResource(R.string.special_five_g_mode_conservative),
                            subtitle = stringResource(R.string.special_five_g_mode_conservative_sub),
                            selected = fiveG.mode == SpecialFeatureStore.FiveGConfig.Mode.CONSERVATIVE,
                            enabled = !busy,
                            onClick = {
                                fiveG = fiveG.copy(
                                    mode = SpecialFeatureStore.FiveGConfig.Mode.CONSERVATIVE,
                                )
                            },
                        )
                        OneToolsGroupDivider()
                        OneToolsSettingsChoiceRow(
                            title = stringResource(R.string.special_five_g_mode_cn),
                            subtitle = stringResource(R.string.special_five_g_mode_cn_sub),
                            selected = fiveG.mode == SpecialFeatureStore.FiveGConfig.Mode.CN_SPEED,
                            enabled = !busy,
                            onClick = {
                                fiveG = fiveG.copy(
                                    mode = SpecialFeatureStore.FiveGConfig.Mode.CN_SPEED,
                                )
                            },
                        )
                        OneToolsGroupDivider()
                        OneToolsSettingsChoiceRow(
                            title = stringResource(R.string.special_five_g_mode_cool),
                            subtitle = stringResource(R.string.special_five_g_mode_cool_sub),
                            selected = fiveG.mode == SpecialFeatureStore.FiveGConfig.Mode.COOL,
                            enabled = !busy,
                            onClick = {
                                fiveG = fiveG.copy(
                                    mode = SpecialFeatureStore.FiveGConfig.Mode.COOL,
                                )
                            },
                        )
                        OneToolsGroupDivider()
                        OneToolsSettingsChoiceRow(
                            title = stringResource(R.string.special_five_g_mode_custom),
                            subtitle = stringResource(R.string.special_five_g_mode_custom_sub),
                            selected = fiveG.mode == SpecialFeatureStore.FiveGConfig.Mode.CUSTOM,
                            enabled = !busy,
                            onClick = {
                                fiveG = fiveG.copy(
                                    mode = SpecialFeatureStore.FiveGConfig.Mode.CUSTOM,
                                )
                            },
                        )
                    }
                    if (fiveG.mode == SpecialFeatureStore.FiveGConfig.Mode.CUSTOM) {
                        OneToolsGroupDivider()
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            FiveGThresholdField(
                                label = stringResource(R.string.special_five_g_threshold_plus_dl),
                                value = fiveG.plusDlThresholdMbps,
                                default = SpecialFeatureStore.FiveGConfig().plusDlThresholdMbps,
                                enabled = !busy,
                                onValueCommitted = {
                                    fiveG = fiveG.copy(plusDlThresholdMbps = it)
                                },
                            )
                            FiveGThresholdField(
                                label = stringResource(R.string.special_five_g_threshold_a_dl),
                                value = fiveG.fiveGaDlThresholdMbps,
                                default = SpecialFeatureStore.FiveGConfig().fiveGaDlThresholdMbps,
                                enabled = !busy,
                                onValueCommitted = {
                                    fiveG = fiveG.copy(fiveGaDlThresholdMbps = it)
                                },
                            )
                            FiveGThresholdField(
                                label = stringResource(
                                    R.string.special_five_g_threshold_ul_enhanced,
                                ),
                                value = fiveG.uplinkEnhancedThresholdMbps,
                                default = SpecialFeatureStore.FiveGConfig()
                                    .uplinkEnhancedThresholdMbps,
                                enabled = !busy,
                                onValueCommitted = {
                                    fiveG = fiveG.copy(uplinkEnhancedThresholdMbps = it)
                                },
                            )
                            FiveGThresholdField(
                                label = stringResource(R.string.special_five_g_threshold_ul_super),
                                value = fiveG.superUplinkThresholdMbps,
                                default = SpecialFeatureStore.FiveGConfig()
                                    .superUplinkThresholdMbps,
                                enabled = !busy,
                                onValueCommitted = {
                                    fiveG = fiveG.copy(superUplinkThresholdMbps = it)
                                },
                            )
                            OutlinedTextField(
                                value = fiveG.systemIconConfigString,
                                onValueChange = {
                                    fiveG = fiveG.copy(systemIconConfigString = it.take(1024))
                                },
                                label = {
                                    Text(stringResource(R.string.special_five_g_system_icon_config))
                                },
                                supportingText = {
                                    Text(
                                        stringResource(
                                            R.string.special_five_g_system_icon_config_sub,
                                        ),
                                    )
                                },
                                enabled = !busy,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                OneToolsGroupDivider()
                Column(modifier = Modifier.padding(20.dp)) {
                    OneToolsPrimaryButton(
                        text = applyFiveGLabel,
                        onClick = {
                            if (selectedSubId < 0) {
                                status = context.getString(R.string.special_data_switch_no_sim)
                                return@OneToolsPrimaryButton
                            }
                            busy = true
                            busyLabel = applyFiveGLabel
                            scope.launch {
                                val msg = withContext(Dispatchers.IO) {
                                    runCatching {
                                        FiveGDisplayController.apply(
                                            context,
                                            selectedSubId,
                                            fiveG,
                                        )
                                    }.getOrElse { it.message ?: it.toString() }
                                }
                                fiveG = SpecialFeatureStore.fiveGConfig(context)
                                status = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                busy = false
                                busyLabel = null
                            }
                        },
                        enabled = actionsEnabled && selectedSubId >= 0,
                        loading = busyLabel == applyFiveGLabel,
                    )
                }
            }
        }

        item {
            OneToolsSection(
                title = stringResource(R.string.special_qs_feature_title),
                description = stringResource(R.string.special_qs_feature_desc),
            ) {
                OneToolsSettingsActionRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.special_qs_open_editor),
                    subtitle = stringResource(R.string.special_qs_manual_guide),
                    onClick = { TileHelper.openTileEditor(context) },
                    enabled = !busy,
                )
                OneToolsGroupDivider()
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sims.isEmpty()) {
                        Text(
                            text = stringResource(R.string.special_data_switch_no_sim),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        sims.forEach { sim ->
                            Text(
                                text = stringResource(
                                    R.string.special_data_switch_row,
                                    sim.slotIndex + 1,
                                    sim.shortName,
                                    if (sim.isDefaultData) {
                                        stringResource(R.string.special_data_switch_current_tag)
                                    } else {
                                        ""
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { refreshSims() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && hasPhonePermission,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            stringResource(R.string.special_data_switch_refresh),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (sims.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            sims.filterNot { it.isDefaultData }.forEach { sim ->
                                OutlinedButton(
                                    onClick = { pendingSwitchSim = sim },
                                    enabled = actionsEnabled,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.special_data_switch_to_card,
                                            sim.slotIndex + 1,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        status?.let { msg ->
            item {
                OneToolsInlineNotice(text = msg, danger = false)
            }
        }
    }
}

@Composable
private fun FiveGThresholdField(
    label: String,
    value: Int,
    default: Int,
    enabled: Boolean,
    onValueCommitted: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val parsed = input.toIntOrNull()
            if (parsed != null && parsed in 1..10_000) {
                onValueCommitted(parsed)
            }
        },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (enabled && !focusState.isFocused) {
                    val parsed = text.toIntOrNull()
                    if (parsed == null || parsed !in 1..10_000) {
                        text = default.toString()
                        onValueCommitted(default)
                    }
                }
            },
    )
}
