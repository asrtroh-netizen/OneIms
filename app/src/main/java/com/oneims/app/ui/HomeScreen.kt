package com.oneims.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneims.app.R

private enum class HomeToolDialog {
    Snapshot,
    History,
    Settings,
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
) {
    val context = LocalContext.current
    var openDialog by remember { mutableStateOf<HomeToolDialog?>(null) }

    OneImsPage(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.home_subtitle),
        sims = state.sims,
        selectedSubId = state.selectedSubId,
        onSelectSim = actions.onSelectSim,
        simSelectionEnabled = state.actionsEnabled,
    ) {
        item {
            StatusHero(
                oneKukuState = state.oneKukuState,
                onPrimaryAction = {
                    when (state.oneKukuState) {
                        OneKukuCardState.INACTIVE -> actions.onActivateOneKuku()
                        OneKukuCardState.SLEEPING -> actions.onRestoreCallConfig()
                        OneKukuCardState.RUNNING -> Unit
                        OneKukuCardState.COMPLETE -> actions.onCheckOneKukuStatus()
                    }
                },
                sims = state.sims,
                selectedSubId = state.selectedSubId,
                deviceInfo = state.deviceInfo,
            )
        }

        item {
            SectionBlock(
                title = stringResource(R.string.home_quick_actions),
                description = stringResource(R.string.home_quick_actions_sub),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionGrid(
                        listOf(
                            ActionSpec(
                                icon = Icons.Filled.Search,
                                title = stringResource(R.string.onekuku_tool_status_title),
                                subtitle = stringResource(R.string.onekuku_tool_status_sub),
                                onClick = actions.onStatusCheck,
                                enabled = state.actionsEnabled,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.onekuku_tool_snapshot_title),
                                subtitle = stringResource(R.string.onekuku_tool_snapshot_sub),
                                onClick = { openDialog = HomeToolDialog.Snapshot },
                            ),
                            ActionSpec(
                                icon = Icons.AutoMirrored.Filled.List,
                                title = stringResource(R.string.onekuku_tool_history_title),
                                subtitle = stringResource(R.string.onekuku_tool_history_sub),
                                onClick = { openDialog = HomeToolDialog.History },
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.onekuku_tool_settings_title),
                                subtitle = stringResource(R.string.onekuku_tool_settings_sub),
                                onClick = { openDialog = HomeToolDialog.Settings },
                            ),
                        ),
                    )
                }
            }
        }

        if (state.sims.isEmpty()) {
            item {
                SettingsActionRow(
                    icon = Icons.Filled.AccountBox,
                    title = stringResource(R.string.no_sim_hint),
                    subtitle = stringResource(R.string.no_sim_detail),
                    onClick = actions.onStatusCheck,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.home_emergency_title),
                description = stringResource(R.string.home_emergency_subtitle),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.home_emergency_restore_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = actions.onRestoreCallConfig,
                        enabled = state.selectedSubId >= 0 &&
                            state.actionsEnabled &&
                            state.oneKukuState != OneKukuCardState.RUNNING,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.onekuku_action_restore),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    when (openDialog) {
        HomeToolDialog.Snapshot -> {
            val lines = OneKukuHomeTools.buildSnapshotLines(context, state.selectedSubId)
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.onekuku_tool_snapshot_title)) },
                text = {
                    if (lines == null) {
                        Text(stringResource(R.string.onekuku_snapshot_empty))
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            lines.forEach { line ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        line.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        line.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { openDialog = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        HomeToolDialog.History -> {
            val status = state.reapplyStatus
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.onekuku_tool_history_title)) },
                text = {
                    if (status == null) {
                        Text(stringResource(R.string.onekuku_history_empty))
                    } else {
                        val failure = OneKukuHomeTools.sanitizeUserText(status.message)
                            .ifBlank { stringResource(R.string.onekuku_history_no_reason) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(
                                    R.string.onekuku_history_time,
                                    OneKukuHomeTools.formatRestoreTime(status.timestampMillis),
                                ),
                            )
                            Text(
                                stringResource(
                                    R.string.onekuku_history_result,
                                    OneKukuHomeTools.restoreResultLabel(context, status),
                                ),
                            )
                            if (!status.success) {
                                Text(stringResource(R.string.onekuku_history_reason, failure))
                            }
                            Text(
                                stringResource(
                                    R.string.onekuku_history_status,
                                    OneKukuHomeTools.settingsStatusLabel(
                                        context = context,
                                        state = state.oneKukuState,
                                        serviceRunning = state.shizukuRunning,
                                    ),
                                ),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { openDialog = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        HomeToolDialog.Settings -> {
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.onekuku_tool_settings_title)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.onekuku_settings_status,
                                OneKukuHomeTools.settingsStatusLabel(
                                    context = context,
                                    state = state.oneKukuState,
                                    serviceRunning = state.shizukuRunning,
                                ),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.onekuku_settings_boot_check),
                            subtitle = stringResource(R.string.onekuku_settings_boot_check_sub),
                            checked = state.bootAutoCheck,
                            onCheckedChange = actions.onBootAutoCheckChange,
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.onekuku_settings_auto_sleep),
                            subtitle = stringResource(R.string.onekuku_settings_auto_sleep_sub),
                            checked = state.autoSleep,
                            onCheckedChange = actions.onAutoSleepChange,
                        )
                        SettingsActionRow(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.onekuku_settings_reactivate),
                            subtitle = stringResource(R.string.onekuku_settings_reactivate_sub),
                            onClick = {
                                openDialog = null
                                actions.onActivateOneKuku()
                            },
                        )
                        SettingsActionRow(
                            icon = Icons.Filled.Search,
                            title = stringResource(R.string.onekuku_settings_view_status),
                            subtitle = stringResource(R.string.onekuku_settings_view_status_sub),
                            onClick = {
                                openDialog = null
                                actions.onStatusCheck()
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { openDialog = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        null -> Unit
    }
}
