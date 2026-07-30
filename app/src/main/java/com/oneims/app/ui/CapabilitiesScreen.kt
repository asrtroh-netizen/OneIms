package com.oneims.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oneims.app.R
import com.oneims.app.core.formatCarrierShortName

private data class VoWifiFormatChoice(
    val index: Int?,
    @StringRes val labelRes: Int,
)

private val voWifiFormatChoices = listOf(
    VoWifiFormatChoice(null, R.string.vowifi_name_format_system),
    VoWifiFormatChoice(0, R.string.vowifi_name_format_carrier),
    VoWifiFormatChoice(1, R.string.vowifi_name_format_carrier_wifi_calling),
    VoWifiFormatChoice(2, R.string.vowifi_name_format_wlan_call),
    VoWifiFormatChoice(3, R.string.vowifi_name_format_carrier_wlan_call),
    VoWifiFormatChoice(4, R.string.vowifi_name_format_carrier_wifi),
    VoWifiFormatChoice(5, R.string.vowifi_name_format_wifi_calling_carrier),
    VoWifiFormatChoice(6, R.string.vowifi_name_format_carrier_vowifi),
    VoWifiFormatChoice(7, R.string.vowifi_name_format_wifi_calling_hyphen),
    VoWifiFormatChoice(8, R.string.vowifi_name_format_wifi_hyphen),
    VoWifiFormatChoice(9, R.string.vowifi_name_format_wifi_calling),
    VoWifiFormatChoice(10, R.string.vowifi_name_format_vowifi),
    VoWifiFormatChoice(11, R.string.vowifi_name_format_carrier_wifi_calling_plain),
    VoWifiFormatChoice(12, R.string.vowifi_name_format_wifi_call),
)

@Composable
fun CapabilitiesScreen(
    state: CapabilitiesUiState,
    actions: CapabilitiesActions,
) {
    var showVoWifiFormatDialog by rememberSaveable { mutableStateOf(false) }
    val selectedVoWifiFormatLabel = stringResource(
        voWifiFormatChoices
            .firstOrNull { choice -> choice.index == state.voWifiNameFormatIndex }
            ?.labelRes
            ?: R.string.vowifi_name_format_system,
    )

    OneImsPage(
        title = stringResource(R.string.capabilities_title),
        subtitle = stringResource(R.string.capabilities_subtitle),
        sims = state.sims,
        selectedSubId = state.selectedSubId,
        onSelectSim = actions.onSelectSim,
        simSelectionEnabled = state.actionsEnabled,
    ) {
        item {
            SettingsGroup {
                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.tool_apn),
                    subtitle = stringResource(R.string.tool_apn_sub_short),
                    onClick = actions.onOpenApnCatalog,
                    enabled = true,
                )
            }
        }

        if (!state.prerequisitesMet) {
            item {
                InlineNotice(
                    text = stringResource(R.string.diagnostics_prereq_short),
                    danger = true,
                )
            }
        }

        item {
            // 五项主能力连排：VoLTE / VoWiFi / VoNR / 5G NR / 5G信号强度调整。
            SectionBlock(
                title = stringResource(R.string.cap_group_radio_title),
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_volte),
                    subtitle = stringResource(R.string.cap_volte_sub),
                    checked = state.volte,
                    onCheckedChange = actions.onVolteChange,
                    icon = Icons.Filled.Call,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_vowifi),
                    subtitle = stringResource(R.string.cap_vowifi_sub),
                    checked = state.vowifi,
                    onCheckedChange = actions.onVowifiChange,
                    icon = Icons.Filled.Call,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_vonr),
                    subtitle = stringResource(R.string.cap_vonr_sub),
                    checked = state.vonr,
                    onCheckedChange = actions.onVonrChange,
                    icon = Icons.Filled.Phone,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_5g_nr),
                    subtitle = stringResource(R.string.cap_5g_nr_sub),
                    checked = state.nr5g,
                    onCheckedChange = actions.onNr5gChange,
                    icon = Icons.Filled.Phone,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.signal_strength_adjust_title),
                    subtitle = stringResource(R.string.signal_strength_adjust_subtitle),
                    checked = state.signalStrengthAdjustmentEnabled,
                    onCheckedChange = actions.onSignalStrengthAdjustmentChange,
                    enabled = state.actionsEnabled,
                    icon = Icons.Filled.Settings,
                )
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OneImsPrimaryButton(
                        text = stringResource(R.string.action_apply_core),
                        onClick = actions.onApplyCore,
                        enabled = state.actionsEnabled,
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.action_apply_core),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GroupDivider()
                Column(
                    modifier = Modifier.padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.selectedSim?.let { sim ->
                        Text(
                            text = stringResource(
                                R.string.system_target_preview,
                                sim.slotIndex + 1,
                                formatCarrierShortName(sim.carrierName),
                                
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.vowifi_name_preview,
                            state.voWifiNamePreview,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    // 格式选项改弹出：能力页只保留当前选中回显，避免 13 项单选铺满一长串。
                    SettingsActionRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.vowifi_name_title),
                        subtitle = stringResource(
                            R.string.vowifi_name_format_pick_sub,
                            selectedVoWifiFormatLabel,
                        ),
                        onClick = { showVoWifiFormatDialog = true },
                        enabled = state.actionsEnabled,
                        trailingText = stringResource(R.string.vowifi_name_format_pick_action),
                    )
                    OutlinedTextField(
                        value = state.voWifiCustomCarrierName,
                        onValueChange = actions.onVoWifiCustomCarrierNameChange,
                        label = {
                            Text(stringResource(R.string.vowifi_name_custom_carrier))
                        },
                        supportingText = {
                            Text(stringResource(R.string.vowifi_name_custom_carrier_hint))
                        },
                        singleLine = true,
                        enabled = state.actionsEnabled && state.voWifiNameFormatIndex != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                    OneImsPrimaryButton(
                        text = stringResource(R.string.vowifi_name_apply),
                        onClick = actions.onApplyVoWifiName,
                        enabled = state.actionsEnabled,
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.vowifi_name_apply),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                }
            }
        }

        item {
            // 合并原「增强能力」与原「高级」页的「运营商显示与漫游选项」为一组：
            // 都是次要/展示层的补充配置，两个子集各自保留独立的应用按钮，逻辑不变。
            SectionBlock(
                title = stringResource(R.string.cap_group_extra_title),
                description = stringResource(R.string.cap_group_extra_sub),
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_vilte),
                    subtitle = stringResource(R.string.cap_vilte_sub),
                    checked = state.vilte,
                    onCheckedChange = actions.onVilteChange,
                    icon = Icons.Filled.Face,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_ut),
                    subtitle = stringResource(R.string.cap_ut_sub),
                    checked = state.ut,
                    onCheckedChange = actions.onUtChange,
                    icon = Icons.Filled.Settings,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.cap_crosssim),
                    subtitle = stringResource(R.string.cap_crosssim_sub),
                    checked = state.crossSim,
                    onCheckedChange = actions.onCrossSimChange,
                    icon = Icons.Filled.Phone,
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    OneImsPrimaryButton(
                        text = stringResource(R.string.action_apply_extras),
                        onClick = actions.onApplyExtras,
                        enabled = state.actionsEnabled,
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.action_apply_extras),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_wfc_roaming),
                    subtitle = stringResource(R.string.advanced_wfc_roaming_desc),
                    checked = state.advancedOptions.wfcRoamingEnabled,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(wfcRoamingEnabled = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_show_wfc_mode),
                    subtitle = stringResource(R.string.advanced_show_wfc_mode_desc),
                    checked = state.advancedOptions.showWfcMode,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(showWfcMode = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_show_wfc_roaming_mode),
                    subtitle = stringResource(R.string.advanced_show_wfc_roaming_mode_desc),
                    checked = state.advancedOptions.showWfcRoamingMode,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(
                            state.advancedOptions.copy(showWfcRoamingMode = it),
                        )
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_wifi_only),
                    subtitle = stringResource(R.string.advanced_wifi_only_desc),
                    checked = state.advancedOptions.supportWifiOnly,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(supportWifiOnly = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_allow_apn_add),
                    subtitle = stringResource(R.string.advanced_allow_apn_add_desc),
                    checked = state.advancedOptions.allowAddingApns,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(allowAddingApns = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_vowifi_icon),
                    subtitle = stringResource(R.string.advanced_vowifi_icon_desc),
                    checked = state.advancedOptions.showVowifiIcon,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(showVowifiIcon = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_data_rat_icon),
                    subtitle = stringResource(R.string.advanced_data_rat_icon_desc),
                    checked = state.advancedOptions.alwaysShowDataRatIcon,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(
                            state.advancedOptions.copy(alwaysShowDataRatIcon = it),
                        )
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_4g_for_lte),
                    subtitle = stringResource(R.string.advanced_4g_for_lte_desc),
                    checked = state.advancedOptions.show4gForLteIcon,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(show4gForLteIcon = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_hide_lte_plus),
                    subtitle = stringResource(R.string.advanced_hide_lte_plus_desc),
                    checked = state.advancedOptions.hideLtePlusIcon,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(hideLtePlusIcon = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_show_ims_status),
                    subtitle = stringResource(R.string.advanced_show_ims_status_desc),
                    checked = state.advancedOptions.showImsStatus,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(showImsStatus = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_ss_over_cdma),
                    subtitle = stringResource(R.string.advanced_ss_over_cdma_desc),
                    checked = state.advancedOptions.ssOverCdma,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(ssOverCdma = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.advanced_enhanced_4g),
                    subtitle = stringResource(R.string.advanced_enhanced_4g_desc),
                    checked = state.advancedOptions.enhanced4g,
                    onCheckedChange = {
                        actions.onAdvancedOptionsChange(state.advancedOptions.copy(enhanced4g = it))
                    },
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OneImsPrimaryButton(
                        text = stringResource(R.string.advanced_apply),
                        onClick = actions.onApplyAdvancedOptions,
                        enabled = state.actionsEnabled,
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.advanced_apply),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            InlineNotice(
                text = stringResource(R.string.diagnostics_fix_order),
            )
        }

        item {
            // 原「实验功能」修复工具中的重启 IMS / 网络修复并入「能力」页；
            // SIM 国家码覆盖与 TikTok 预设留在独家功能页。
            // 离线 APN 库仍留在实验功能页（更像数据资源查询，而非直接改配置的修复动作）。
            SectionBlock(
                title = stringResource(R.string.diagnostics_repairs),
                description = stringResource(R.string.diagnostics_repairs_sub),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Refresh,
                    title = stringResource(R.string.tool_restart_ims),
                    subtitle = stringResource(R.string.tool_restart_ims_sub),
                    onClick = actions.onRestartIms,
                    enabled = state.actionsEnabled,
                )
                GroupDivider()
                SettingsActionRow(
                    icon = Icons.Filled.LocationOn,
                    title = stringResource(R.string.action_net_fix),
                    subtitle = stringResource(R.string.action_net_fix_sub),
                    onClick = actions.onFixNetwork,
                    enabled = state.actionsEnabled,
                )
            }
        }
    }

    if (showVoWifiFormatDialog) {
        VoWifiFormatPickerDialog(
            selectedIndex = state.voWifiNameFormatIndex,
            enabled = state.actionsEnabled,
            onSelect = { index ->
                actions.onVoWifiNameFormatChange(index)
                showVoWifiFormatDialog = false
            },
            onDismiss = { showVoWifiFormatDialog = false },
        )
    }
}

@Composable
private fun VoWifiFormatPickerDialog(
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .heightIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.vowifi_name_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.one_click_close),
                            )
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = voWifiFormatChoices,
                            key = { _, choice -> choice.index ?: -1 },
                        ) { choiceIndex, choice ->
                            if (choiceIndex > 0) GroupDivider()
                            SettingsChoiceRow(
                                title = stringResource(choice.labelRes),
                                subtitle = stringResource(
                                    R.string.vowifi_name_format_index,
                                    choice.index?.toString()
                                        ?: stringResource(R.string.vowifi_name_format_system_value),
                                ),
                                selected = selectedIndex == choice.index,
                                onClick = { onSelect(choice.index) },
                                enabled = enabled,
                            )
                        }
                    }
                }
            }
        }
    }
}




