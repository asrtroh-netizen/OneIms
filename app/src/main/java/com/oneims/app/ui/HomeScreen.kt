package com.oneims.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import com.oneims.app.core.ChannelLine
import com.oneims.app.core.OneKukuEmbeddedAdbActivator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class HomeToolDialog {
    Status,
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
                channelSleeping = state.oneKukuChannelSleeping,
                onPrimaryAction = {
                    when (state.oneKukuState) {
                        // OneLink：新 UI + 2.0.9 逻辑 → 直接开 Shizuku，不弹内嵌 ADB 三步说明。
                        // OneKuku：已配对直连；从未配对才出无线调试说明 + 通知栏填码。
                        OneKukuCardState.INACTIVE -> {
                            if (ChannelLine.usesShizuku ||
                                OneKukuEmbeddedAdbActivator.hasPairedOnce(context)
                            ) {
                                actions.onActivateOneKuku()
                            } else {
                                openDialog = HomeToolDialog.WirelessGuide
                            }
                        }
                        OneKukuCardState.READY,
                        OneKukuCardState.SLEEPING,
                        -> actions.onCheckOneKukuStatus()
                        OneKukuCardState.ACTIVATING -> Unit
                    }
                },
                onOpenDeviceDetails = { openDialog = HomeToolDialog.DeviceInfo },
                detailOverride = state.oneKukuDetailOverride,
            )
        }

        item {
            CarrierRecommendCard(
                sims = state.sims,
                selectedSim = state.selectedSim,
                actionsEnabled = state.recommendActionsEnabled,
                applying = state.activeOperationLabel ==
                    stringResource(R.string.apply_recommended),
                onApplyRecommended = actions.onApplyRecommended,
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
                                // 与「保存」成对：同一通道就绪门禁，避免无快照时误触写回。
                                enabled = state.actionsEnabled &&
                                    state.oneKukuState == OneKukuCardState.READY &&
                                    state.sims.isNotEmpty(),
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
    }

    when (openDialog) {
        HomeToolDialog.WirelessGuide -> {
            // 弹窗一出现就挂通知栏填码入口并切出「未激活」红态，别等确定后再干等 mDNS。
            LaunchedEffect(Unit) {
                actions.onBeginWirelessPairGuide()
            }
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
                            // 通知已在弹窗出现时挂出；此处只继续激活，内部勿再并发抢开无线调试。
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

        null -> Unit
    }
}
