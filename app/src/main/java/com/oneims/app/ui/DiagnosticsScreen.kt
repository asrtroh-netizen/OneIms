package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import com.oneims.app.core.CheckStatus
import com.oneims.app.core.DiagnosticCheckItem
import com.oneims.app.core.UserFacingDiagnosticItem
import com.oneims.app.core.FixOutcome
import com.oneims.app.core.formatCarrierShortName
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    actions: DiagnosticsActions,
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkResults by remember { mutableStateOf<List<UserFacingDiagnosticItem>?>(null) }
    var fixing by remember { mutableStateOf(false) }
    var fixOutcomes by remember { mutableStateOf<List<Pair<DiagnosticCheckItem, FixOutcome>>?>(null) }
    val selectedSim = state.sims.firstOrNull { sim ->
        sim.subscriptionId == state.selectedSubId
    }

    OneImsPage(
        title = stringResource(R.string.diagnostics_title),
        subtitle = stringResource(R.string.diagnostics_subtitle),
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
            SectionBlock(title = stringResource(R.string.one_click_section_title)) {
                if (selectedSim != null) {
                    Text(
                        text = stringResource(
                            R.string.system_target_preview,
                            selectedSim.slotIndex + 1,
                            formatCarrierShortName(selectedSim.carrierName),
                            selectedSim.subscriptionId,
                        ),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OneImsPrimaryButton(
                        text = stringResource(R.string.one_click_check_action),
                        onClick = {
                            val targetSubId = state.selectedSubId
                            checking = true
                            scope.launch {
                                checkResults = actions.onRunUserFacingCheck(targetSubId)
                                checking = false
                            }
                        },
                        enabled = state.actionsEnabled && !checking && !fixing,
                        loading = checking,
                        loadingText = stringResource(R.string.one_click_check_checking),
                        modifier = Modifier.weight(1f),
                    )
                    OneImsPrimaryButton(
                        text = stringResource(R.string.one_click_fix_action),
                        onClick = {
                            val targetSubId = state.selectedSubId
                            fixing = true
                            scope.launch {
                                val items = actions.onRunDiagnosticsCheck(targetSubId)
                                val outcomes = items.map { item ->
                                    val outcome = if (item.status == CheckStatus.FAIL) {
                                        actions.onAutoFixDiagnosticsItem(targetSubId, item)
                                    } else {
                                        FixOutcome(item.id, fixed = false, message = "")
                                    }
                                    item to outcome
                                }
                                fixOutcomes = outcomes
                                checkResults = actions.onRunUserFacingCheck(targetSubId)
                                fixing = false
                            }
                        },
                        enabled = state.actionsEnabled && !fixing && !checking,
                        loading = fixing,
                        loadingText = stringResource(R.string.one_click_fix_fixing),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.diagnostics_checks),
                description = stringResource(R.string.diagnostics_checks_sub),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionGrid(
                        listOf(
                            ActionSpec(
                                icon = Icons.Filled.Favorite,
                                title = stringResource(R.string.tool_health),
                                subtitle = stringResource(R.string.tool_health_sub),
                                onClick = actions.onHealthCheck,
                                enabled = state.actionsEnabled,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Search,
                                title = stringResource(R.string.tool_epdg),
                                subtitle = stringResource(R.string.tool_epdg_sub),
                                onClick = actions.onCheckEpdg,
                                enabled = state.actionsEnabled,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Search,
                                title = stringResource(R.string.tool_diag),
                                subtitle = stringResource(R.string.tool_diag_sub),
                                onClick = actions.onQueryIms,
                                enabled = state.actionsEnabled,
                            ),
                            ActionSpec(
                                icon = Icons.AutoMirrored.Filled.List,
                                title = stringResource(R.string.tool_config),
                                subtitle = stringResource(R.string.tool_config_sub),
                                onClick = actions.onDumpConfig,
                                enabled = state.actionsEnabled,
                            ),
                        ),
                    )
                }
            }
        }

        item {
            // 原「首页」的 Shizuku 起不来自救入口，原样搬入。
            SectionBlock(
                title = stringResource(R.string.shizuku_setup_title),
                description = stringResource(R.string.shizuku_setup_subtitle),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionGrid(
                        listOf(
                            ActionSpec(
                                icon = Icons.Filled.Phone,
                                title = stringResource(R.string.setup_open_shizuku),
                                subtitle = stringResource(R.string.setup_open_shizuku_sub),
                                onClick = actions.onOpenShizuku,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Build,
                                title = stringResource(R.string.setup_wireless_debug),
                                subtitle = stringResource(R.string.setup_wireless_debug_sub),
                                onClick = actions.onOpenWirelessDebugging,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.LocationOn,
                                title = stringResource(R.string.setup_hotspot),
                                subtitle = stringResource(R.string.setup_hotspot_sub),
                                onClick = actions.onOpenHotspot,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Share,
                                title = stringResource(R.string.setup_copy_cmd),
                                subtitle = stringResource(R.string.setup_copy_cmd_sub),
                                onClick = actions.onCopySetupCommand,
                            ),
                        ),
                    )
                }
            }
        }

        item {
            // 原「高级」页的重新应用当前配置，原样搬入。
            SectionBlock(
                title = stringResource(R.string.action_reapply),
                description = stringResource(R.string.reapply_explanation),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Refresh,
                    title = stringResource(R.string.action_reapply),
                    subtitle = reapplyStatusLine(state),
                    onClick = actions.onReapply,
                    enabled = state.actionsEnabled,
                )
                state.reapplyStatus?.message?.takeIf(String::isNotBlank)?.let { message ->
                    GroupDivider()
                    InlineNotice(
                        text = message,
                        danger = !state.reapplyStatus.success,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        item {
            // 原「高级」页的完整 CarrierConfig 导出，原样搬入。
            SectionBlock(
                title = stringResource(R.string.advanced_export_title),
                description = stringResource(R.string.advanced_export_desc),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.advanced_export_action),
                    subtitle = stringResource(R.string.tool_config_sub),
                    onClick = actions.onExportFullConfig,
                    enabled = state.actionsEnabled,
                )
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.log_title),
                description = stringResource(R.string.log_subtitle),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = state.log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = actions.onCopyLog,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text(
                                stringResource(R.string.action_copy_log),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        TextButton(
                            onClick = actions.onClearLog,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(
                                stringResource(R.string.action_clear_log),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    checkResults?.let { items ->
        AlertDialog(
            onDismissRequest = { checkResults = null },
            title = { Text(stringResource(R.string.one_click_check_result_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { item -> UserCheckItemRow(item) }
                }
            },
            confirmButton = {
                TextButton(onClick = { checkResults = null }) {
                    Text(stringResource(R.string.one_click_close))
                }
            },
        )
    }

    fixOutcomes?.let { outcomes ->
        AlertDialog(
            onDismissRequest = { fixOutcomes = null },
            title = { Text(stringResource(R.string.one_click_fix_result_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    outcomes.forEach { (item, outcome) -> FixOutcomeRow(item, outcome) }
                }
            },
            confirmButton = {
                TextButton(onClick = { fixOutcomes = null }) {
                    Text(stringResource(R.string.one_click_close))
                }
            },
        )
    }
}

/** 用户向体检弹窗行 */
@Composable
private fun UserCheckItemRow(item: UserFacingDiagnosticItem) {
    Row(verticalAlignment = Alignment.Top) {
        val (icon, tint) = when (item.status) {
            CheckStatus.PASS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            CheckStatus.FAIL -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
            CheckStatus.UNKNOWN -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(18.dp),
        )
        Column {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 体检弹窗里的一行：todo-list 风格，图标一眼看出正常/异常/无法判定。 */
@Composable
private fun CheckItemRow(item: DiagnosticCheckItem) {
    Row(verticalAlignment = Alignment.Top) {
        val (icon, tint) = when (item.status) {
            CheckStatus.PASS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            CheckStatus.FAIL -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
            CheckStatus.UNKNOWN -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(18.dp),
        )
        Column {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 修复结果弹窗里的一行：已修复 / 建议手动处理的指引 / 本身正常。 */
@Composable
private fun FixOutcomeRow(item: DiagnosticCheckItem, outcome: FixOutcome) {
    Row(verticalAlignment = Alignment.Top) {
        val fixed = outcome.fixed
        val (icon, tint) = when {
            item.status != CheckStatus.FAIL -> Icons.Filled.CheckCircle to
                MaterialTheme.colorScheme.onSurfaceVariant
            fixed -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            else -> Icons.Filled.Info to MaterialTheme.colorScheme.error
        }
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(18.dp),
        )
        Column {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            val message = when {
                item.status != CheckStatus.FAIL && item.status != CheckStatus.UNKNOWN ->
                    stringResource(R.string.one_click_fix_result_ok)
                item.status == CheckStatus.UNKNOWN -> stringResource(R.string.one_click_fix_result_unknown)
                fixed -> stringResource(R.string.one_click_fix_result_fixed)
                else -> stringResource(R.string.one_click_fix_result_guidance, item.guidance)
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun reapplyStatusLine(state: DiagnosticsUiState): String {
    val context = LocalContext.current
    val status = state.reapplyStatus ?: return stringResource(R.string.reapply_status_none)
    val timestamp = remember(status.timestampMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(status.timestampMillis))
    }
    return context.getString(
        R.string.reapply_status_line,
        context.getString(status.trigger.labelRes),
        context.getString(
            if (status.success) {
                R.string.reapply_status_success
            } else {
                R.string.reapply_status_failed
            },
        ),
        timestamp,
    )
}
