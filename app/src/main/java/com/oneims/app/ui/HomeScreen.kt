package com.oneims.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneims.app.R

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
) {
    val ready = state.shizukuGranted && state.selectedSim != null
    val statusSubtitle = when {
        !state.shizukuRunning -> stringResource(R.string.home_status_shizuku_off)
        !state.shizukuGranted -> stringResource(R.string.home_status_permission)
        state.selectedSim == null -> stringResource(R.string.home_status_no_sim)
        else -> stringResource(R.string.home_status_ready)
    }

    OneImsPage(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.home_subtitle),
        sims = state.sims,
        selectedSubId = state.selectedSubId,
        onSelectSim = actions.onSelectSim,
        simSelectionEnabled = state.actionsEnabled,
    ) {
        item {
            // 设备详情与移动网络展示都并入同一张 Hero 卡：审美与形状语言保持不变，
            // 只在内部按需追加胶囊分页（只读展示每张卡状态）与可展开的设备详情文本。
            StatusHero(
                ready = ready,
                title = stringResource(
                    if (ready) R.string.home_ready_title else R.string.home_setup_needed_title,
                ),
                subtitle = statusSubtitle,
                supportingText = stringResource(R.string.home_safety_note),
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
                    // SIM 状态已在上方 StatusHero 展示，此处不再重复放胶囊分页
                    ActionGrid(
                        listOf(
                            ActionSpec(
                                icon = Icons.Filled.Refresh,
                                title = stringResource(R.string.action_refresh),
                                subtitle = stringResource(R.string.action_refresh_sub),
                                onClick = actions.onRefresh,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.CheckCircle,
                                title = stringResource(R.string.action_compat),
                                subtitle = stringResource(R.string.action_compat_sub),
                                onClick = actions.onCompatibilityCheck,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Lock,
                                title = stringResource(R.string.action_grant),
                                subtitle = stringResource(R.string.action_grant_sub),
                                onClick = actions.onGrantShizuku,
                                enabled = state.shizukuRunning && !state.shizukuGranted,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Refresh,
                                title = stringResource(R.string.action_restore),
                                subtitle = stringResource(R.string.action_restore_sub),
                                onClick = actions.onRestoreDefaults,
                                enabled = state.selectedSubId >= 0 && state.actionsEnabled,
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
                    onClick = actions.onRefresh,
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
                        stringResource(R.string.home_emergency_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = actions.onRestoreDefaults,
                        enabled = state.selectedSubId >= 0 && state.actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.action_restore),
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
