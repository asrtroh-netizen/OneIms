package com.onetools.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.meter.MeterDisplayMode
import com.onetools.app.meter.MeterOverlayController
import com.onetools.app.meter.MeterSettings
import com.onetools.app.meter.SpeedMonitorService
import kotlinx.coroutines.launch

@Composable
fun MeterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { MeterSettings(context.applicationContext) }
    val prefs by settings.snapshotFlow.collectAsState(
        initial = com.onetools.app.meter.MeterPrefsSnapshot(),
    )
    var running by remember { mutableStateOf(SpeedMonitorService.isRunning) }
    var prefixDraft by remember(prefs.prefix) { mutableStateOf(prefs.prefix) }

    DisposableEffect(Unit) {
        onDispose { }
    }

    fun applyAndRestartPrefs() {
        context.startService(
            Intent(context, SpeedMonitorService::class.java)
                .setAction(SpeedMonitorService.ACTION_APPLY_PREFS),
        )
    }

    ToolScaffold(
        title = stringResource(R.string.meter_title),
        onBack = onBack,
    ) {
        item {
            Text(
                stringResource(R.string.meter_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            if (running) {
                Button(
                    onClick = {
                        SpeedMonitorService.stop(context)
                        running = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.meter_stop)) }
            } else {
                Button(
                    onClick = {
                        SpeedMonitorService.start(context)
                        running = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.meter_start)) }
            }
        }
        item {
            Text(stringResource(R.string.meter_mode_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MeterDisplayMode.entries.forEach { mode ->
                    val label = when (mode) {
                        MeterDisplayMode.BOTH -> stringResource(R.string.meter_mode_both)
                        MeterDisplayMode.TOTAL -> stringResource(R.string.meter_mode_total)
                        MeterDisplayMode.DOWN -> stringResource(R.string.meter_mode_down)
                        MeterDisplayMode.UP -> stringResource(R.string.meter_mode_up)
                    }
                    val selected = prefs.displayMode == mode
                    if (selected) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settings.setDisplayMode(mode)
                                    applyAndRestartPrefs()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = prefixDraft,
                onValueChange = { prefixDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.meter_prefix_hint)) },
                singleLine = true,
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        settings.setPrefix(prefixDraft)
                        applyAndRestartPrefs()
                        Toast.makeText(context, R.string.meter_saved, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.meter_save_prefix)) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = prefs.notificationEnabled,
                    onCheckedChange = {
                        scope.launch {
                            settings.setNotificationEnabled(it)
                            applyAndRestartPrefs()
                        }
                    },
                )
                Text(stringResource(R.string.meter_notif_enable))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = prefs.overlayEnabled,
                    onCheckedChange = { enable ->
                        scope.launch {
                            if (enable && !MeterOverlayController(context).canDraw()) {
                                Toast.makeText(context, R.string.meter_overlay_need_perm, Toast.LENGTH_LONG).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                context.startActivity(intent)
                            }
                            settings.setOverlayEnabled(enable)
                            if (enable && !SpeedMonitorService.isRunning) {
                                SpeedMonitorService.start(context)
                                running = true
                            }
                            applyAndRestartPrefs()
                        }
                    },
                )
                Text(stringResource(R.string.meter_overlay_enable))
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.meter_overlay_settings)) }
        }
        item {
            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("https://speed.cloudflare.com/")
                    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, R.string.meter_speedtest_fail, Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.meter_speedtest)) }
        }
        item {
            Text(
                stringResource(R.string.meter_tile_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.meter_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← $title") }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
