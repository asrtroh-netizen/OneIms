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

import androidx.compose.material.icons.filled.Info

import androidx.compose.material.icons.filled.Lock

import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Settings

import androidx.compose.material.icons.filled.Star

import androidx.compose.material.icons.filled.Warning

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



            SectionBlock(title = stringResource(R.string.root_persist_section_title)) {



                SettingsSwitchRow(



                    title = stringResource(R.string.root_boot_title),



                    subtitle = stringResource(R.string.root_boot_sub),



                    checked = state.rootBootStart,



                    onCheckedChange = actions.onRootBootStartChange,



                    icon = Icons.Filled.Star,



                )



                GroupDivider()



                SettingsSwitchRow(



                    title = stringResource(R.string.root_persist_title),



                    subtitle = stringResource(R.string.root_persist_sub),



                    checked = state.rootPersistEnhance,



                    onCheckedChange = actions.onRootPersistEnhanceChange,



                    icon = Icons.Filled.Star,



                )



                GroupDivider()



                SettingsActionRow(



                    icon = Icons.Filled.Info,



                    title = stringResource(R.string.root_persist_status_label),



                    subtitle = state.rootPersistStatusDetail.ifBlank {



                        stringResource(R.string.root_persist_last_unknown)



                    },



                    onClick = null,



                )



            }



        }



        item {



            SectionBlock(title = stringResource(R.string.force_temporary_section_title)) {



                SettingsSwitchRow(



                    title = stringResource(R.string.force_temporary_title),



                    subtitle = stringResource(R.string.force_temporary_sub),



                    checked = state.forceTemporaryOverride,



                    onCheckedChange = actions.onForceTemporaryOverrideChange,



                    icon = Icons.Filled.Build,



                )



            }



        }



        item {



            SectionBlock(title = stringResource(R.string.system_update_shield_title)) {



                SettingsSwitchRow(



                    title = stringResource(R.string.system_update_shield_title),



                    subtitle = stringResource(R.string.system_update_shield_sub),



                    checked = state.systemUpdateShield,



                    onCheckedChange = actions.onSystemUpdateShieldChange,



                    icon = Icons.Filled.Warning,



                )



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
