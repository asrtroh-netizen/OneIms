package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import com.oneims.app.core.formatCarrierShortName
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    actions: DiagnosticsActions,
) {
    val scope = rememberCoroutineScope()
    var pendingTrial by remember { mutableStateOf<DiagnosticsTrialAction?>(null) }
    var detailTitle by remember { mutableStateOf<String?>(null) }
    var detailBody by remember { mutableStateOf<String?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    val selectedSim = state.sims.firstOrNull { sim ->
        sim.subscriptionId == state.selectedSubId
    }

    fun openDetail(title: String, block: suspend () -> String) {
        detailTitle = title
        detailBody = null
        detailLoading = true
        scope.launch {
            detailBody = runCatching { block() }.getOrElse { error ->
                error.message ?: error.javaClass.simpleName
            }
            detailLoading = false
        }
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

        if (selectedSim != null) {
            item {
                Text(
                    text = stringResource(
                        R.string.system_target_preview,
                        selectedSim.slotIndex + 1,
                        formatCarrierShortName(selectedSim.carrierName),
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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
                    val healthTitle = stringResource(R.string.tool_health)
                    val epdgTitle = stringResource(R.string.tool_epdg)
                    val diagTitle = stringResource(R.string.tool_diag)
                    val configTitle = stringResource(R.string.tool_config)
                    ActionGrid(
                        listOf(
                            ActionSpec(
                                icon = Icons.Filled.Favorite,
                                title = healthTitle,
                                subtitle = stringResource(R.string.tool_health_sub),
                                onClick = {
                                    openDetail(healthTitle) { actions.onHealthCheck() }
                                },
                                enabled = state.actionsEnabled && !detailLoading,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Search,
                                title = epdgTitle,
                                subtitle = stringResource(R.string.tool_epdg_sub),
                                onClick = {
                                    openDetail(epdgTitle) { actions.onCheckEpdg() }
                                },
                                enabled = state.actionsEnabled && !detailLoading,
                            ),
                            ActionSpec(
                                icon = Icons.Filled.Search,
                                title = diagTitle,
                                subtitle = stringResource(R.string.tool_diag_sub),
                                onClick = {
                                    openDetail(diagTitle) { actions.onQueryIms() }
                                },
                                enabled = state.actionsEnabled && !detailLoading,
                            ),
                            ActionSpec(
                                icon = Icons.AutoMirrored.Filled.List,
                                title = configTitle,
                                subtitle = stringResource(R.string.tool_config_sub),
                                onClick = {
                                    openDetail(configTitle) { actions.onDumpConfig() }
                                },
                                enabled = state.actionsEnabled && !detailLoading,
                            ),
                        ),
                    )
                }
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.diagnostics_trial_section_title),
                description = stringResource(R.string.diagnostics_trial_section_sub),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Refresh,
                    title = stringResource(R.string.action_reapply),
                    subtitle = reapplyStatusLine(state),
                    onClick = { pendingTrial = DiagnosticsTrialAction.REAPPLY },
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
                GroupDivider()
                SettingsActionRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.advanced_export_action),
                    subtitle = stringResource(R.string.advanced_export_desc),
                    onClick = { pendingTrial = DiagnosticsTrialAction.EXPORT },
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
                    OutlinedButton(
                        onClick = actions.onExportDetailLog,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Text(
                            stringResource(R.string.action_export_detail_log),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    pendingTrial?.let { trial ->
        AlertDialog(
            onDismissRequest = { pendingTrial = null },
            title = { Text(stringResource(R.string.diagnostics_trial_title)) },
            text = {
                Text(
                    stringResource(
                        when (trial) {
                            DiagnosticsTrialAction.REAPPLY -> R.string.diagnostics_trial_reapply_body
                            DiagnosticsTrialAction.EXPORT -> R.string.diagnostics_trial_export_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (trial) {
                            DiagnosticsTrialAction.REAPPLY -> actions.onReapply()
                            DiagnosticsTrialAction.EXPORT -> actions.onExportFullConfig()
                        }
                        pendingTrial = null
                    },
                ) {
                    Text(stringResource(R.string.diagnostics_trial_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTrial = null }) {
                    Text(stringResource(R.string.diagnostics_trial_cancel))
                }
            },
        )
    }

    if (detailTitle != null) {
        AlertDialog(
            onDismissRequest = {
                if (!detailLoading) {
                    detailTitle = null
                    detailBody = null
                }
            },
            title = { Text(detailTitle.orEmpty()) },
            text = {
                SelectionContainer {
                    Text(
                        text = when {
                            detailLoading -> stringResource(R.string.operation_already_running)
                            else -> detailBody.orEmpty()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailTitle = null
                        detailBody = null
                    },
                    enabled = !detailLoading,
                ) {
                    Text(stringResource(R.string.one_click_close))
                }
            },
        )
    }
}

private enum class DiagnosticsTrialAction {
    REAPPLY,
    EXPORT,
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
