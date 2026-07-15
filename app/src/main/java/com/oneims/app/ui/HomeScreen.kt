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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class HomeToolDialog {
    Status,
    Snapshot,
    History,
    Settings,
    DeviceInfo,
    WirelessGuide,
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
                        // 未激活/失败：先弹出图四三步说明，确定后再激活并打开无线调试。
                        OneKukuCardState.INACTIVE,
                        OneKukuCardState.FAILED,
                        -> openDialog = HomeToolDialog.WirelessGuide
                        OneKukuCardState.READY -> actions.onCheckOneKukuStatus()
                        OneKukuCardState.ACTIVATING,
                        OneKukuCardState.EXECUTING,
                        -> Unit
                    }
                },
                onOpenDeviceDetails = { openDialog = HomeToolDialog.DeviceInfo },
                detailOverride = state.oneKukuDetailOverride,
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
                                onClick = { openDialog = HomeToolDialog.Status },
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
                    onClick = { openDialog = HomeToolDialog.Status },
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
                    val restoring = state.oneKukuState == OneKukuCardState.EXECUTING
                    Button(
                        onClick = actions.onRestoreCallConfig,
                        enabled = state.actionsEnabled && !restoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                        )
                        Text(
                            stringResource(
                                if (restoring) {
                                    R.string.onekuku_action_running
                                } else {
                                    R.string.onekuku_action_restore
                                },
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    when (openDialog) {
        HomeToolDialog.WirelessGuide -> {
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.home_adb_prep_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.home_adb_prep_steps),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openDialog = null
                            // prepareOneKukuCore 内部已打开无线调试；勿再并发跳一次，避免抢 ADB 会话。
                            actions.onActivateOneKuku()
                        },
                    ) {
                        Text(stringResource(R.string.home_adb_wireless_guide_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openDialog = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        HomeToolDialog.DeviceInfo -> {
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.home_device_details)) },
                text = {
                    Text(
                        text = state.deviceInfo.ifBlank {
                            stringResource(R.string.onekuku_snapshot_empty)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { openDialog = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        HomeToolDialog.Status -> {
            var lines by remember { mutableStateOf<List<OneKukuHomeTools.SnapshotLine>?>(null) }
            LaunchedEffect(
                state.selectedSubId,
                state.oneKukuState,
                state.shizukuRunning,
                state.shizukuGranted,
            ) {
                lines = withContext(Dispatchers.IO) {
                    OneKukuHomeTools.buildStatusCheckLines(
                        context = context,
                        selectedSubId = state.selectedSubId,
                        cardState = state.oneKukuState,
                        serviceRunning = state.shizukuRunning,
                        serviceGranted = state.shizukuGranted,
                        sims = state.sims,
                    )
                }
            }
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.onekuku_tool_status_title)) },
                text = {
                    if (lines == null) {
                        Text(stringResource(R.string.onekuku_busy_status_check))
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            lines.orEmpty().forEach { line ->
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
            val lines = OneKukuHomeTools.buildRestoreHistoryLines(context)
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text(stringResource(R.string.onekuku_tool_history_title)) },
                text = {
                    if (lines == null) {
                        Text(stringResource(R.string.onekuku_history_empty))
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
                            title = stringResource(R.string.onekuku_settings_auto_restore),
                            subtitle = stringResource(R.string.onekuku_settings_auto_restore_sub),
                            checked = state.autoRestore,
                            onCheckedChange = actions.onAutoRestoreChange,
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
                            title = stringResource(R.string.onekuku_settings_check_status),
                            subtitle = stringResource(R.string.onekuku_settings_check_status_sub),
                            onClick = {
                                openDialog = null
                                actions.onCheckOneKukuStatus()
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
