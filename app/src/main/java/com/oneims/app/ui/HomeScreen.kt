package com.oneims.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.oneims.app.R
import com.oneims.app.core.ChannelLine
import com.oneims.app.core.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class HomeToolDialog {
    Status,
    WirelessGuide,
    TerminalTip,
    RootTip,
    AdbTip,
    DeviceDetails,
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
) {
    if (!ChannelLine.usesShizuku) {
        OneKukuStandaloneHome(state = state, actions = actions)
    } else {
        OneLinkHome(state = state, actions = actions)
    }
}

/**
 * 独立版首页：与 Lite 同结构 / 同 [StatusHero] 配色——
 * 状态卡 → 快速开始 → 设备详情（底）；无「快速入口」四格与无线启动卡。
 */
@Composable
private fun OneKukuStandaloneHome(
    state: HomeUiState,
    actions: HomeActions,
) {
    var openDialog by remember { mutableStateOf<HomeToolDialog?>(null) }

    OneImsPage(
        title = stringResource(R.string.channel_display_name),
        subtitle = stringResource(R.string.onekuku_home_subtitle),
        sims = state.sims,
        selectedSubId = state.selectedSubId,
        onSelectSim = actions.onSelectSim,
        simSelectionEnabled = state.actionsEnabled,
    ) {
        item {
            StatusHero(
                oneKukuState = state.oneKukuState,
                channelSleeping = state.oneKukuChannelSleeping,
                onPrimaryAction = {
                    when (state.oneKukuState) {
                        OneKukuCardState.INACTIVE -> actions.onActivateOneKuku()
                        OneKukuCardState.READY,
                        OneKukuCardState.SLEEPING,
                        -> actions.onCheckOneKukuStatus()
                        OneKukuCardState.ACTIVATING -> Unit
                    }
                },
                onOpenDeviceDetails = { openDialog = HomeToolDialog.DeviceDetails },
                detailOverride = state.oneKukuDetailOverride,
            )
        }

        item {
            RootBootHomeCard(
                checked = state.rootBootStart,
                onCheckedChange = actions.onRootBootStartChange,
            )
        }

        item {
            SandboxPersistHomeCard(
                checked = state.sandboxPersistBypass,
                onCheckedChange = actions.onSandboxPersistBypassChange,
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
                                icon = Icons.Filled.Refresh,
                                title = stringResource(R.string.onekuku_action_save_config),
                                subtitle = stringResource(R.string.onekuku_action_save_config_sub),
                                onClick = actions.onSaveCallConfig,
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
                            ),
                            ActionSpec(
                                icon = Icons.Filled.PlayArrow,
                                title = stringResource(R.string.onekuku_action_restore),
                                subtitle = stringResource(R.string.onekuku_action_restore_sub),
                                onClick = actions.onRestoreCallConfig,
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Warning,
                                title = stringResource(R.string.action_restore),
                                subtitle = stringResource(R.string.action_restore_sub),
                                onClick = actions.onRestoreSystemDefaults,
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
                                danger = true,
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
            DeviceDetailsCard(embedded = false)
        }
    }

    OneKukuHomeDialogs(
        openDialog = openDialog,
        onDismiss = { openDialog = null },
        state = state,
        actions = actions,
    )
}

/** OneLink / Lite：保留原首页（状态卡 + 运营商 + 保存恢复 + 设备卡）。 */
@Composable
private fun OneLinkHome(
    state: HomeUiState,
    actions: HomeActions,
) {
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
                channelSleeping = state.oneKukuChannelSleeping,
                onPrimaryAction = {
                    when (state.oneKukuState) {
                        OneKukuCardState.INACTIVE -> actions.onActivateOneKuku()
                        OneKukuCardState.READY,
                        OneKukuCardState.SLEEPING,
                        -> actions.onCheckOneKukuStatus()
                        OneKukuCardState.ACTIVATING -> Unit
                    }
                },
                onOpenDeviceDetails = { openDialog = HomeToolDialog.DeviceDetails },
                detailOverride = state.oneKukuDetailOverride,
            )
        }

        item {
            RootBootHomeCard(
                checked = state.rootBootStart,
                onCheckedChange = actions.onRootBootStartChange,
            )
        }

        item {
            SandboxPersistHomeCard(
                checked = state.sandboxPersistBypass,
                onCheckedChange = actions.onSandboxPersistBypassChange,
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
                                icon = Icons.Filled.Refresh,
                                title = stringResource(R.string.onekuku_action_save_config),
                                subtitle = stringResource(R.string.onekuku_action_save_config_sub),
                                onClick = actions.onSaveCallConfig,
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
                            ),
                            ActionSpec(
                                icon = Icons.Filled.PlayArrow,
                                title = stringResource(R.string.onekuku_action_restore),
                                subtitle = stringResource(R.string.onekuku_action_restore_sub),
                                onClick = actions.onRestoreCallConfig,
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Warning,
                                title = stringResource(R.string.action_restore),
                                subtitle = stringResource(R.string.action_restore_sub),
                                onClick = actions.onRestoreSystemDefaults,
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
                                danger = true,
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

        // Lite：设备详情卡固定在首页最下方（不依赖弹窗）。
        item {
            DeviceDetailsCard(embedded = false)
        }
    }

    OneKukuHomeDialogs(
        openDialog = openDialog,
        onDismiss = { openDialog = null },
        state = state,
        actions = actions,
    )
}

/** 首页 Root 开机自启框：与无线自启同级心智——有 Root、没无线也能拉起通道。 */
@Composable
private fun RootBootHomeCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.root_boot_home_title),
            subtitle = stringResource(R.string.root_boot_home_sub),
            checked = checked,
            onCheckedChange = onCheckedChange,
            icon = Icons.Filled.Star,
        )
    }
}

/** 首页「持久性VoLTE/NR」：与 Root 自启同级一块开关，默认开。 */
@Composable
private fun SandboxPersistHomeCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.sandbox_persist_title),
            subtitle = stringResource(R.string.sandbox_persist_home_sub),
            checked = checked,
            onCheckedChange = onCheckedChange,
            icon = Icons.Filled.Info,
        )
    }
}

@Composable
private fun OneKukuHomeDialogs(
    openDialog: HomeToolDialog?,
    onDismiss: () -> Unit,
    state: HomeUiState,
    actions: HomeActions,
) {
    val context = LocalContext.current
    when (openDialog) {
        HomeToolDialog.WirelessGuide -> {
            LaunchedEffect(Unit) {
                actions.onBeginWirelessPairGuide()
            }
            AlertDialog(
                onDismissRequest = onDismiss,
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
                            onDismiss()
                            actions.onActivateOneKuku()
                        },
                    ) {
                        Text(stringResource(R.string.home_adb_wireless_guide_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
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
                onDismissRequest = onDismiss,
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
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        HomeToolDialog.TerminalTip -> {
            val channelReady = state.oneKukuState == OneKukuCardState.READY ||
                state.oneKukuState == OneKukuCardState.SLEEPING
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.onekuku_home_tile_terminal)) },
                text = {
                    Text(
                        stringResource(
                            if (channelReady) R.string.onekuku_home_tile_terminal_ready_tip
                            else R.string.onekuku_home_tile_terminal_need_ready,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        HomeToolDialog.RootTip -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.onekuku_home_tile_root)) },
                text = { Text(stringResource(R.string.onekuku_home_tile_root_detail)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        HomeToolDialog.AdbTip -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.onekuku_home_tile_adb)) },
                text = { Text(stringResource(R.string.onekuku_home_tile_adb_ready_tip)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismiss()
                            actions.onActivateOneKuku()
                        },
                    ) {
                        Text(stringResource(R.string.onekuku_action_check))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        HomeToolDialog.DeviceDetails -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.home_device_details)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        DeviceDetailsCard(embedded = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }

        null -> Unit
    }
}

@Composable
private fun DeviceDetailsCard(embedded: Boolean = false) {
    val context = LocalContext.current
    val snap = remember { DeviceInfo.snapshot(context) }
    // 跟主题走：弹窗/深色模式下硬编码深字会几乎看不见。
    val contentColor = MaterialTheme.colorScheme.onSurface

    @Composable
    fun Body() {
        Column(
            modifier = Modifier.padding(if (embedded) 0.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!embedded) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = contentColor,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_device_details),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.72f),
                        )
                        Text(
                            text = snap.modelTitle(),
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.home_device_version_line,
                                snap.versionName,
                                snap.versionCode,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.72f),
                        )
                    }
                }
            } else {
                Text(
                    text = snap.modelTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.home_device_version_line,
                        snap.versionName,
                        snap.versionCode,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DeviceInfoChip(
                    label = stringResource(R.string.home_device_chip_android),
                    value = "Android ${snap.androidRelease}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f),
                )
                DeviceInfoChip(
                    label = stringResource(R.string.home_device_chip_tensor),
                    value = snap.tensorLabel,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DeviceInfoChip(
                    label = stringResource(R.string.home_device_chip_sim),
                    value = snap.simCount.toString(),
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f),
                )
                DeviceInfoChip(
                    label = stringResource(R.string.home_device_chip_delegate),
                    value = snap.delegateLabel,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceInfoMetaRow(
                    label = stringResource(R.string.home_device_meta_codename),
                    value = snap.device,
                    contentColor = contentColor,
                )
                DeviceInfoMetaRow(
                    label = stringResource(R.string.home_device_meta_patch),
                    value = snap.securityPatch,
                    contentColor = contentColor,
                )
                DeviceInfoMetaRow(
                    label = stringResource(R.string.home_device_meta_strategy),
                    value = snap.strategyLabel,
                    contentColor = contentColor,
                )
            }
        }
    }

    if (embedded) {
        Body()
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
        ) {
            Body()
        }
    }
}

@Composable
private fun DeviceInfoChip(
    label: String,
    value: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = contentColor.copy(alpha = 0.06f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.62f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DeviceInfoMetaRow(
    label: String,
    value: String,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.62f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}
