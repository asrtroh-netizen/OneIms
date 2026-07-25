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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.onetools.app.R
import com.onetools.app.meter.AppTrafficReader
import com.onetools.app.meter.AppTrafficRow
import com.onetools.app.meter.MeterDisplayMode
import com.onetools.app.meter.MeterOverlayController
import com.onetools.app.meter.MeterOverlayTheme
import com.onetools.app.meter.MeterRateUnit
import com.onetools.app.meter.MeterSettings
import com.onetools.app.meter.MeterSpeedOrder
import com.onetools.app.meter.SpeedMonitorService
import com.onetools.app.meter.TrafficNetwork
import com.onetools.app.meter.TrafficPeriod
import com.onetools.app.meter.TrafficVolumeFormat
import com.onetools.app.meter.UsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MeterScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { MeterSettings(context.applicationContext) }
    val prefs by settings.snapshotFlow.collectAsState(
        initial = com.onetools.app.meter.MeterPrefsSnapshot(),
    )
    var running by remember { mutableStateOf(SpeedMonitorService.isRunning) }
    var prefixDraft by remember(prefs.prefix) { mutableStateOf(prefs.prefix) }
    var trafficPeriod by remember { mutableStateOf(TrafficPeriod.TODAY) }
    var trafficNetwork by remember { mutableStateOf(TrafficNetwork.ALL) }
    var hasUsageAccess by remember { mutableStateOf(UsageAccess.hasPermission(context)) }
    var trafficRows by remember { mutableStateOf<List<AppTrafficRow>>(emptyList()) }
    var trafficError by remember { mutableStateOf<String?>(null) }
    var trafficLoading by remember { mutableStateOf(false) }
    var trafficTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val trafficReader = remember { AppTrafficReader(context.applicationContext) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasUsageAccess = UsageAccess.hasPermission(context)
            trafficTick++
        }
    }

    LaunchedEffect(hasUsageAccess, trafficPeriod, trafficNetwork, trafficTick) {
        if (!hasUsageAccess) {
            trafficRows = emptyList()
            trafficError = null
            trafficLoading = false
            return@LaunchedEffect
        }
        trafficLoading = true
        trafficError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { trafficReader.load(trafficPeriod, trafficNetwork) }
        }
        trafficLoading = false
        result.onSuccess { trafficRows = it }
            .onFailure { trafficError = it.message ?: it.javaClass.simpleName }
    }

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
        showBack = showBack,
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
            Text(stringResource(R.string.meter_apps_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                stringResource(R.string.meter_apps_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!hasUsageAccess) {
            item {
                Text(
                    stringResource(R.string.meter_apps_need_perm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            item {
                Button(
                    onClick = { UsageAccess.openSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.meter_apps_open_perm)) }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        TrafficPeriod.TODAY to R.string.meter_apps_period_today,
                        TrafficPeriod.DAYS_7 to R.string.meter_apps_period_7d,
                        TrafficPeriod.DAYS_30 to R.string.meter_apps_period_30d,
                    ).forEach { (period, labelRes) ->
                        val selected = trafficPeriod == period
                        val label = stringResource(labelRes)
                        if (selected) {
                            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { trafficPeriod = period },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        TrafficNetwork.ALL to R.string.meter_apps_net_all,
                        TrafficNetwork.WIFI to R.string.meter_apps_net_wifi,
                        TrafficNetwork.MOBILE to R.string.meter_apps_net_mobile,
                    ).forEach { (net, labelRes) ->
                        val selected = trafficNetwork == net
                        val label = stringResource(labelRes)
                        if (selected) {
                            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { trafficNetwork = net },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { trafficTick++ },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.meter_apps_refresh)) }
            }
            when {
                trafficLoading -> item {
                    Text(stringResource(R.string.meter_starting), style = MaterialTheme.typography.bodyMedium)
                }
                trafficError != null -> item {
                    Text(
                        stringResource(R.string.meter_apps_error, trafficError!!),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                trafficRows.isEmpty() -> item {
                    Text(
                        stringResource(R.string.meter_apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    items(trafficRows.size) { index ->
                        val row = trafficRows[index]
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(row.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                row.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.meter_apps_row,
                                    TrafficVolumeFormat.formatBytes(row.rxBytes),
                                    TrafficVolumeFormat.formatBytes(row.txBytes),
                                    TrafficVolumeFormat.formatBytes(row.totalBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.meter_apps_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            Text(stringResource(R.string.meter_order_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    MeterSpeedOrder.DOWN_THEN_UP to stringResource(R.string.meter_order_down_up),
                    MeterSpeedOrder.UP_THEN_DOWN to stringResource(R.string.meter_order_up_down),
                ).forEach { (order, label) ->
                    val selected = prefs.speedOrder == order
                    if (selected) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settings.setSpeedOrder(order)
                                    applyAndRestartPrefs()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.meter_unit_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    MeterRateUnit.BYTES_PER_SEC to stringResource(R.string.meter_unit_bytes),
                    MeterRateUnit.BITS_PER_SEC to stringResource(R.string.meter_unit_bits),
                ).forEach { (unit, label) ->
                    val selected = prefs.rateUnit == unit
                    if (selected) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settings.setRateUnit(unit)
                                    applyAndRestartPrefs()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
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
            Text(stringResource(R.string.meter_oem_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                stringResource(R.string.meter_oem_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = prefs.statusBarChipEnabled,
                    onCheckedChange = {
                        scope.launch {
                            settings.setStatusBarChipEnabled(it)
                            if (it && !prefs.notificationEnabled) {
                                settings.setNotificationEnabled(true)
                            }
                            if (it && !SpeedMonitorService.isRunning) {
                                SpeedMonitorService.start(context)
                                running = true
                            }
                            applyAndRestartPrefs()
                        }
                    },
                )
                Text(stringResource(R.string.meter_chip_enable))
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (!MeterOverlayController(context).canDraw()) {
                            Toast.makeText(context, R.string.meter_oem_dock_need_overlay, Toast.LENGTH_LONG).show()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                            return@launch
                        }
                        if (!SpeedMonitorService.isRunning) {
                            SpeedMonitorService.start(context)
                            running = true
                        }
                        context.startService(
                            Intent(context, SpeedMonitorService::class.java)
                                .setAction(SpeedMonitorService.ACTION_DOCK_OEM),
                        )
                        Toast.makeText(context, R.string.meter_oem_docked, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.meter_oem_dock)) }
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
            Text(stringResource(R.string.meter_style_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                stringResource(R.string.meter_style_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                MeterOverlayTheme.entries.forEach { theme ->
                    val label = when (theme) {
                        MeterOverlayTheme.ONE_DARK -> stringResource(R.string.meter_theme_one_dark)
                        MeterOverlayTheme.ONE_MIST -> stringResource(R.string.meter_theme_one_mist)
                        MeterOverlayTheme.ONE_WHITE -> stringResource(R.string.meter_theme_one_white)
                        MeterOverlayTheme.SLATE -> stringResource(R.string.meter_theme_slate)
                    }
                    val selected = prefs.overlayTheme == theme
                    if (selected) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settings.setOverlayTheme(theme)
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
            Text(stringResource(R.string.meter_style_text, prefs.overlayTextSp.toInt()))
            Slider(
                value = prefs.overlayTextSp,
                onValueChange = { v ->
                    scope.launch {
                        settings.setOverlayTextSp(v)
                        applyAndRestartPrefs()
                    }
                },
                valueRange = 10f..22f,
            )
        }
        item {
            Text(stringResource(R.string.meter_style_corner, prefs.overlayCornerDp.toInt()))
            Slider(
                value = prefs.overlayCornerDp,
                onValueChange = { v ->
                    scope.launch {
                        settings.setOverlayCornerDp(v)
                        applyAndRestartPrefs()
                    }
                },
                valueRange = 4f..28f,
            )
        }
        item {
            Text(stringResource(R.string.meter_style_alpha, (prefs.overlayAlpha * 100).toInt()))
            Slider(
                value = prefs.overlayAlpha,
                onValueChange = { v ->
                    scope.launch {
                        settings.setOverlayAlpha(v)
                        applyAndRestartPrefs()
                    }
                },
                valueRange = 0.35f..1f,
            )
        }
        item {
            Text(stringResource(R.string.meter_style_pad_h, prefs.overlayPadHDp.toInt()))
            Slider(
                value = prefs.overlayPadHDp,
                onValueChange = { v ->
                    scope.launch {
                        settings.setOverlayPadding(v, prefs.overlayPadVDp)
                        applyAndRestartPrefs()
                    }
                },
                valueRange = 8f..28f,
            )
        }
        item {
            Text(stringResource(R.string.meter_style_pad_v, prefs.overlayPadVDp.toInt()))
            Slider(
                value = prefs.overlayPadVDp,
                onValueChange = { v ->
                    scope.launch {
                        settings.setOverlayPadding(prefs.overlayPadHDp, v)
                        applyAndRestartPrefs()
                    }
                },
                valueRange = 6f..20f,
            )
        }
        item {
            Text(stringResource(R.string.meter_interval_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(500L, 1000L, 2000L).forEach { ms ->
                    val label = stringResource(R.string.meter_interval_ms, ms.toInt())
                    val selected = prefs.sampleIntervalMs == ms
                    if (selected) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settings.setSampleIntervalMs(ms)
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
            Text(
                stringResource(R.string.meter_overlay_gestures),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    showBack: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    if (showBack) {
        OneToolsToolPage(title = title, onBack = onBack, content = content)
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
