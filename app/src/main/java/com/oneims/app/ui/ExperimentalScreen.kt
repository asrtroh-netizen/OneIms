package com.oneims.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.IdentityInputPolicy
import com.oneims.app.core.SimCountryIsoManager
import com.oneims.app.core.SimpleFiveGDisplayConfig
import com.oneims.app.core.formatCarrierShortName

@Composable

fun ExperimentalScreen(

    state: ExperimentalUiState,

    actions: ExperimentalActions,

) {

    val carrierNameError = IdentityInputPolicy.carrierNameError(state.carrierName)

    val userAgentError = IdentityInputPolicy.imsUserAgentError(state.imsUserAgent)

    val currentCarrierName = if (state.currentCarrierName.isBlank()) {

        stringResource(R.string.unknown)

    } else {

        state.currentCarrierName

    }

    var expertKey by remember { mutableStateOf("") }

    var expertValue by remember { mutableStateOf("") }

    val targetSim = state.sims.firstOrNull { it.subscriptionId == state.selectedSubId }

    OneImsPage(

        title = stringResource(R.string.experimental_title),

        subtitle = stringResource(R.string.experimental_subtitle),

        sims = state.sims,

        selectedSubId = state.selectedSubId,

        onSelectSim = actions.onSelectSim,

        simSelectionEnabled = state.actionsEnabled,

    ) {

        if (!state.prerequisitesMet) {

            item {

                InlineNotice(

                    text = stringResource(R.string.diagnostics_prereq_short),

                    danger = true,

                )

            }

        }

        item {

            SectionBlock(title = stringResource(R.string.power_identity_title)) {

                Column(

                    modifier = Modifier.padding(20.dp),

                    verticalArrangement = Arrangement.spacedBy(14.dp),

                ) {

                    if (targetSim != null) {

                        Text(

                            text = stringResource(

                                R.string.identity_target_preview,

                                targetSim.slotIndex + 1,

                                formatCarrierShortName(targetSim.carrierName),

                                targetSim.subscriptionId,

                            ),

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                        )

                    }

                    OutlinedTextField(

                        value = state.carrierName,

                        onValueChange = actions.onCarrierNameChange,

                        label = { Text(stringResource(R.string.hint_carrier_name)) },

                        leadingIcon = {

                            Icon(Icons.Filled.AccountBox, contentDescription = null)

                        },

                        supportingText = {

                            Text(

                                when (carrierNameError) {

                                    IdentityInputPolicy.Error.CONTROL_CHARACTER ->

                                        stringResource(R.string.identity_invalid_control)

                                    IdentityInputPolicy.Error.TOO_LONG ->

                                        stringResource(R.string.identity_invalid_length)

                                    null -> stringResource(

                                        R.string.identity_current_name,

                                        currentCarrierName,

                                    )

                                },

                            )

                        },

                        isError = carrierNameError != null,

                        singleLine = true,

                        modifier = Modifier.fillMaxWidth(),

                    )

                    OutlinedTextField(

                        value = state.imsUserAgent,

                        onValueChange = actions.onImsUserAgentChange,

                        label = { Text(stringResource(R.string.hint_ims_ua)) },

                        leadingIcon = {

                            Icon(Icons.Filled.Settings, contentDescription = null)

                        },

                        supportingText = userAgentError?.let { error ->

                            {

                                Text(

                                    stringResource(

                                        when (error) {

                                            IdentityInputPolicy.Error.CONTROL_CHARACTER ->

                                                R.string.identity_invalid_control

                                            IdentityInputPolicy.Error.TOO_LONG ->

                                                R.string.identity_invalid_length

                                        },

                                    ),

                                )

                            }

                        },

                        isError = userAgentError != null,

                        singleLine = true,

                        modifier = Modifier.fillMaxWidth(),

                    )

                    OneImsPrimaryButton(
                        text = stringResource(R.string.action_apply_identity),
                        onClick = actions.onApplyIdentity,
                        enabled = state.actionsEnabled &&
                            carrierNameError == null &&
                            userAgentError == null &&
                            (state.carrierName.isNotBlank() || state.imsUserAgent.isNotBlank()),
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.action_apply_identity),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TextButton(

                        onClick = actions.onResetCarrierName,

                        enabled = state.actionsEnabled,

                        modifier = Modifier.fillMaxWidth(),

                    ) {

                        Text(stringResource(R.string.action_restore_identity_name))

                    }

                }

            }

        }

        item {
            SectionBlock(
                title = stringResource(R.string.sim_country_title),
                description = stringResource(R.string.sim_country_sub),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (state.activeSimCountryIso.isBlank()) {
                            stringResource(R.string.sim_country_current_none)
                        } else {
                            stringResource(
                                R.string.sim_country_current,
                                state.activeSimCountryIso.uppercase(java.util.Locale.ROOT),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = state.simCountryIso,
                        onValueChange = actions.onSimCountryIsoChange,
                        label = { Text(stringResource(R.string.sim_country_input_label)) },
                        singleLine = true,
                        enabled = state.actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SimCountryIsoManager.presets.forEach { (iso, labelRes) ->
                            FilterChip(
                                selected = state.simCountryIso.equals(iso, ignoreCase = true),
                                onClick = { actions.onSimCountryIsoChange(iso) },
                                label = { Text(stringResource(labelRes)) },
                                enabled = state.actionsEnabled,
                            )
                        }
                    }
                    OneImsPrimaryButton(
                        text = stringResource(R.string.sim_country_apply),
                        onClick = actions.onApplySimCountryIso,
                        enabled = state.actionsEnabled && state.simCountryIso.isNotBlank(),
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.sim_country_apply),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = actions.onClearSimCountryIso,
                        enabled = state.actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.sim_country_clear))
                    }
                    SettingsActionRow(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.action_tiktok_fix),
                        subtitle = stringResource(R.string.action_tiktok_fix_sub),
                        onClick = actions.onApplyTiktokFix,
                        enabled = state.actionsEnabled,
                    )
                }
            }
        }

        item {

            SectionBlock(title = stringResource(R.string.guard_section_title)) {

                SettingsSwitchRow(

                    title = stringResource(R.string.guard_title),

                    subtitle = stringResource(R.string.guard_sub_short),

                    checked = state.guardEnabled,

                    onCheckedChange = actions.onGuardEnabledChange,

                    icon = Icons.Filled.Lock,

                )

            }

        }

        item {
            SectionBlock(title = stringResource(R.string.signal_bar_style_title)) {
                val mode = state.signalBarDisplayMode
                if (targetSim != null) {
                    Text(
                        text = stringResource(
                            R.string.system_target_preview,
                            targetSim.slotIndex + 1,
                            formatCarrierShortName(targetSim.carrierName),
                            targetSim.subscriptionId,
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    GroupDivider()
                }
                SettingsChoiceRow(
                    title = stringResource(R.string.signal_bar_style_auto),
                    subtitle = stringResource(R.string.signal_bar_style_auto_sub),
                    selected = mode == ConfigStore.SignalBarDisplayMode.AUTO,
                    enabled = state.actionsEnabled,
                    onClick = {
                        actions.onSignalBarDisplayModeChange(ConfigStore.SignalBarDisplayMode.AUTO)
                    },
                )
                GroupDivider()
                SettingsChoiceRow(
                    title = stringResource(R.string.signal_bar_style_four),
                    subtitle = stringResource(R.string.signal_bar_style_four_sub),
                    selected = mode == ConfigStore.SignalBarDisplayMode.FOUR_BARS,
                    enabled = state.actionsEnabled,
                    onClick = {
                        actions.onSignalBarDisplayModeChange(
                            ConfigStore.SignalBarDisplayMode.FOUR_BARS,
                        )
                    },
                )
                GroupDivider()
                SettingsChoiceRow(
                    title = stringResource(R.string.signal_bar_style_five),
                    subtitle = stringResource(R.string.signal_bar_style_five_sub),
                    selected = mode == ConfigStore.SignalBarDisplayMode.FIVE_BARS,
                    enabled = state.actionsEnabled,
                    onClick = {
                        actions.onSignalBarDisplayModeChange(
                            ConfigStore.SignalBarDisplayMode.FIVE_BARS,
                        )
                    },
                )
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.signal_bar_style_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneImsPrimaryButton(
                        text = stringResource(R.string.signal_bar_style_apply),
                        onClick = actions.onApplySignalBarStyle,
                        enabled = state.actionsEnabled,
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.signal_bar_style_apply),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            SectionBlock(title = stringResource(R.string.five_g_display_title)) {

                val config = state.fiveGDisplayConfig

                if (targetSim != null) {

                    Text(

                        text = stringResource(

                            R.string.system_target_preview,

                            targetSim.slotIndex + 1,

                            formatCarrierShortName(targetSim.carrierName),

                            targetSim.subscriptionId,

                        ),

                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.primary,

                    )

                    GroupDivider()

                }

                SettingsSwitchRow(

                    title = stringResource(R.string.five_g_display_enable),

                    subtitle = stringResource(R.string.five_g_display_in_app_only),

                    checked = config.enabled,

                    onCheckedChange = {

                        actions.onFiveGDisplayConfigChange(config.copy(enabled = it))

                    },

                    enabled = state.actionsEnabled,

                    icon = Icons.Filled.Star,

                )

                if (config.enabled) {

                    GroupDivider()

                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {

                        SettingsChoiceRow(

                            title = stringResource(R.string.five_g_mode_conservative),

                            subtitle = stringResource(R.string.five_g_mode_conservative_sub),

                            selected = config.mode == SimpleFiveGDisplayConfig.Mode.CONSERVATIVE,
                            enabled = state.actionsEnabled,

                            onClick = {

                                actions.onFiveGDisplayConfigChange(

                                    config.copy(mode = SimpleFiveGDisplayConfig.Mode.CONSERVATIVE),

                                )

                            },

                        )

                        GroupDivider()

                        SettingsChoiceRow(

                            title = stringResource(R.string.five_g_mode_cn_speed),

                            subtitle = stringResource(R.string.five_g_mode_cn_speed_sub),

                            selected = config.mode == SimpleFiveGDisplayConfig.Mode.CN_SPEED,
                            enabled = state.actionsEnabled,

                            onClick = {

                                actions.onFiveGDisplayConfigChange(

                                    config.copy(mode = SimpleFiveGDisplayConfig.Mode.CN_SPEED),

                                )

                            },

                        )

                        GroupDivider()

                        SettingsChoiceRow(

                            title = stringResource(R.string.five_g_mode_cool),

                            subtitle = stringResource(R.string.five_g_mode_cool_sub),

                            selected = config.mode == SimpleFiveGDisplayConfig.Mode.COOL,
                            enabled = state.actionsEnabled,

                            onClick = {

                                actions.onFiveGDisplayConfigChange(

                                    config.copy(mode = SimpleFiveGDisplayConfig.Mode.COOL),

                                )

                            },

                        )

                        GroupDivider()

                        SettingsChoiceRow(

                            title = stringResource(R.string.five_g_mode_custom),

                            subtitle = stringResource(R.string.five_g_mode_custom_sub),

                            selected = config.mode == SimpleFiveGDisplayConfig.Mode.CUSTOM,
                            enabled = state.actionsEnabled,

                            onClick = {

                                actions.onFiveGDisplayConfigChange(

                                    config.copy(mode = SimpleFiveGDisplayConfig.Mode.CUSTOM),

                                )

                            },

                        )

                    }

                    if (config.mode == SimpleFiveGDisplayConfig.Mode.CUSTOM) {

                        GroupDivider()

                        Column(

                            modifier = Modifier.padding(20.dp),

                            verticalArrangement = Arrangement.spacedBy(14.dp),

                        ) {

                            FiveGThresholdField(

                                label = stringResource(R.string.five_g_threshold_plus_dl),

                                value = config.plusDlThresholdMbps,

                                default = SimpleFiveGDisplayConfig().plusDlThresholdMbps,
                                enabled = state.actionsEnabled,

                                onValueCommitted = { threshold ->

                                    actions.onFiveGDisplayConfigChange(

                                        config.copy(plusDlThresholdMbps = threshold),

                                    )

                                },

                            )

                            FiveGThresholdField(

                                label = stringResource(R.string.five_g_threshold_a_dl),

                                value = config.fiveGaDlThresholdMbps,

                                default = SimpleFiveGDisplayConfig().fiveGaDlThresholdMbps,
                                enabled = state.actionsEnabled,

                                onValueCommitted = { threshold ->

                                    actions.onFiveGDisplayConfigChange(

                                        config.copy(fiveGaDlThresholdMbps = threshold),

                                    )

                                },

                            )

                            FiveGThresholdField(

                                label = stringResource(R.string.five_g_threshold_ul_enhanced),

                                value = config.uplinkEnhancedThresholdMbps,

                                default = SimpleFiveGDisplayConfig().uplinkEnhancedThresholdMbps,
                                enabled = state.actionsEnabled,

                                onValueCommitted = { threshold ->

                                    actions.onFiveGDisplayConfigChange(

                                        config.copy(uplinkEnhancedThresholdMbps = threshold),

                                    )

                                },

                            )

                            FiveGThresholdField(

                                label = stringResource(R.string.five_g_threshold_ul_super),

                                value = config.superUplinkThresholdMbps,

                                default = SimpleFiveGDisplayConfig().superUplinkThresholdMbps,
                                enabled = state.actionsEnabled,

                                onValueCommitted = { threshold ->

                                    actions.onFiveGDisplayConfigChange(

                                        config.copy(superUplinkThresholdMbps = threshold),

                                    )

                                },

                            )

                            OutlinedTextField(

                                value = config.systemIconConfigString,

                                onValueChange = { value ->

                                    actions.onFiveGDisplayConfigChange(

                                        config.copy(systemIconConfigString = value.take(1024)),

                                    )

                                },

                                label = {

                                    Text(stringResource(R.string.five_g_system_icon_config))

                                },

                                supportingText = {

                                    Text(stringResource(R.string.five_g_system_icon_config_sub))

                                },

                                enabled = state.actionsEnabled,
                                singleLine = true,

                                modifier = Modifier.fillMaxWidth(),

                            )

                        }

                    }

                }

                GroupDivider()

                Column(modifier = Modifier.padding(20.dp)) {

                    OneImsPrimaryButton(

                        text = stringResource(R.string.five_g_apply),

                        onClick = actions.onApplyFiveGDisplay,

                        enabled = state.actionsEnabled && targetSim != null,

                        loading = state.activeOperationLabel ==
                            stringResource(R.string.five_g_apply),

                        loadingText = stringResource(R.string.action_applying),

                    )

                }

            }

        }

        item {
            SectionBlock(
                title = stringResource(R.string.qs_tile_feature_title),
                description = stringResource(R.string.qs_tile_feature_desc),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.qs_tile_open_editor),
                    subtitle = stringResource(R.string.qs_tile_manual_guide),
                    onClick = actions.onOpenTileSettings,
                )
                GroupDivider()
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.activeDataSims.isEmpty()) {
                        Text(
                            text = stringResource(R.string.data_switch_no_sim),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.activeDataSims.forEach { sim ->
                            Text(
                                text = stringResource(
                                    R.string.data_switch_row,
                                    sim.slotIndex + 1,
                                    sim.shortName,
                                    sim.subId,
                                    if (sim.isDefaultData) {
                                        stringResource(R.string.data_switch_current_tag)
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
                        onClick = actions.onRefreshDataSims,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            stringResource(R.string.data_switch_refresh),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (state.activeDataSims.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.activeDataSims
                                .filterNot { it.isDefaultData }
                                .forEach { sim ->
                                    OutlinedButton(
                                        onClick = { actions.onSwitchDataSim(sim.subId) },
                                        enabled = state.actionsEnabled,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            stringResource(
                                                R.string.data_switch_to_card,
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

        item {
            SectionBlock(title = stringResource(R.string.tool_apn)) {

                SettingsActionRow(

                    icon = Icons.AutoMirrored.Filled.List,

                    title = stringResource(R.string.tool_apn),

                    subtitle = stringResource(R.string.tool_apn_sub_short),

                    onClick = actions.onOpenApnCatalog,

                    enabled = state.catalogEnabled,

                )

            }

        }

        item {

            SectionBlock(title = stringResource(R.string.advanced_expert_title)) {

                Column(

                    modifier = Modifier.padding(20.dp),

                    verticalArrangement = Arrangement.spacedBy(12.dp),

                ) {

                    OutlinedTextField(

                        value = expertKey,

                        onValueChange = { expertKey = it },

                        label = { Text(stringResource(R.string.advanced_expert_key)) },

                        singleLine = true,

                        enabled = state.actionsEnabled,

                        modifier = Modifier.fillMaxWidth(),

                    )

                    OutlinedTextField(

                        value = expertValue,

                        onValueChange = { expertValue = it },

                        label = { Text(stringResource(R.string.advanced_expert_value)) },

                        singleLine = true,

                        enabled = state.actionsEnabled,

                        modifier = Modifier.fillMaxWidth(),

                    )

                    OneImsPrimaryButton(
                        text = stringResource(R.string.advanced_expert_apply),
                        onClick = { actions.onApplyExpertValue(expertKey, expertValue) },
                        enabled = state.actionsEnabled &&
                            expertKey.isNotBlank() &&
                            expertValue.isNotBlank(),
                        loading = state.activeOperationLabel ==
                            stringResource(R.string.advanced_expert_apply),
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )

                }

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

            if (parsed != null && parsed in 1..10000) {

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

                    if (parsed == null || parsed !in 1..10000) {

                        text = default.toString()

                        onValueCommitted(default)

                    }

                }

            },

    )

}

