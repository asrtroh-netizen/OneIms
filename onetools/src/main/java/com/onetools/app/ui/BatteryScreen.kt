package com.onetools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.battery.BatteryAppDrainStore
import com.onetools.app.battery.BatteryCapacityEstimator
import com.onetools.app.battery.BatteryChargeService
import com.onetools.app.battery.BatteryHealthSummary
import com.onetools.app.battery.BatteryPrefs
import com.onetools.app.battery.BatteryPrefsSnapshot
import com.onetools.app.battery.BatteryReader
import com.onetools.app.battery.BatterySessionEntity
import com.onetools.app.battery.BatterySessionStore
import com.onetools.app.battery.BatterySnapshot
import com.onetools.app.battery.healthSummary
import com.onetools.app.meter.UsageAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefsStore = remember { BatteryPrefs(context.applicationContext) }
    val sessionStore = remember { BatterySessionStore(context.applicationContext) }
    val drainStore = remember { BatteryAppDrainStore(context.applicationContext) }
    val prefs by prefsStore.snapshotFlow.collectAsState(
        initial = BatteryPrefsSnapshot(
            designCapacityMah = BatteryPrefs.DEFAULT_DESIGN_MAH,
            chargeAlarmEnabled = true,
            chargeAlarmPercent = BatteryPrefs.DEFAULT_ALARM_PERCENT,
            trackingEnabled = true,
        ),
    )
    val sessions by sessionStore.sessions.collectAsState(initial = emptyList())
    val drainRows by drainStore.observeToday().collectAsState(initial = emptyList())
    var hasUsage by remember { mutableStateOf(UsageAccess.hasPermission(context)) }
    val scope = rememberCoroutineScope()

    var snap by remember { mutableStateOf<BatterySnapshot?>(null) }
    var health by remember { mutableStateOf<BatteryHealthSummary?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var designText by remember { mutableStateOf("") }
    var alarmText by remember { mutableStateOf("") }

    LaunchedEffect(prefs.trackingEnabled, prefs.designCapacityMah) {
        while (true) {
            snap = BatteryReader.read(context)
            health = sessionStore.healthSummary(prefs.designCapacityMah)
            val s = snap
            if (prefs.trackingEnabled) {
                BatteryChargeService.start(context.applicationContext)
            }
            delay(2000)
        }
    }

    LaunchedEffect(prefs.designCapacityMah, prefs.chargeAlarmPercent) {
        designText = prefs.designCapacityMah.toString()
        alarmText = prefs.chargeAlarmPercent.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.battery_accu_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        stringResource(R.string.battery_tab_live),
                        stringResource(R.string.battery_tab_drain),
                        stringResource(R.string.battery_tab_health),
                        stringResource(R.string.battery_tab_history),
                        stringResource(R.string.battery_tab_settings),
                    ).forEachIndexed { index, label ->
                        FilterChip(
                            selected = tab == index,
                            onClick = { tab = index },
                            label = { Text(label) },
                        )
                    }
                }
            }

            when (tab) {
                0 -> {
                    val s = snap
                    if (s == null) {
                        item { Text(stringResource(R.string.battery_unavailable)) }
                    } else {
                        item { MetricZh(stringResource(R.string.battery_level), "${s.percent}%") }
                        item { MetricZh(stringResource(R.string.battery_health_sys), s.healthLabel) }
                        item {
                            MetricZh(
                                stringResource(R.string.battery_temp),
                                "%.1f C".format(s.temperatureC),
                            )
                        }
                        item {
                            MetricZh(stringResource(R.string.battery_voltage), "${s.voltageMv} mV")
                        }
                        item { MetricZh(stringResource(R.string.battery_status), s.statusLabel) }
                        item { MetricZh(stringResource(R.string.battery_power), s.pluggedLabel) }
                        item { MetricZh(stringResource(R.string.battery_tech), s.technology) }
                        item {
                            MetricZh(
                                stringResource(R.string.battery_cycles),
                                if (s.cycleCount >= 0) s.cycleCount.toString() else "N/A",
                            )
                        }
                        item {
                            MetricZh(
                                stringResource(R.string.battery_counter),
                                if (s.chargeCounterMah >= 0) "${s.chargeCounterMah} mAh" else "N/A",
                            )
                        }
                        item {
                            MetricZh(
                                stringResource(R.string.battery_current),
                                "${s.currentNowMa} mA",
                            )
                        }
                        item {
                            val remain = s.chargingTimeRemainingMs
                            MetricZh(
                                stringResource(R.string.battery_charge_eta),
                                if (remain > 0) "${remain / 60_000} min" else "N/A",
                            )
                        }
                    }
                }
                1 -> {
                    item {
                        Text(
                            stringResource(R.string.battery_drain_today),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.battery_drain_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!hasUsage) {
                        item {
                            Text(
                                stringResource(R.string.battery_drain_need_usage),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    UsageAccess.openSettings(context)
                                    hasUsage = UsageAccess.hasPermission(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.battery_drain_open_usage))
                            }
                        }
                    }
                    if (drainRows.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.battery_drain_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(drainRows.take(40), key = { it.id }) { row ->
                            Text(
                                stringResource(
                                    R.string.battery_drain_row,
                                    row.label,
                                    row.mahTotal,
                                    row.mahScreenOn,
                                    row.mahScreenOff,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { hasUsage = UsageAccess.hasPermission(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.battery_drain_refresh))
                        }
                    }
                }
                2 -> {
                    val h = health
                    item {
                        Text(
                            stringResource(R.string.battery_health_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        MetricZh(
                            stringResource(R.string.battery_estimated_mah),
                            h?.estimatedMah?.let { "$it mAh" }
                                ?: stringResource(R.string.battery_need_samples),
                        )
                    }
                    item {
                        MetricZh(
                            stringResource(R.string.battery_design_mah),
                            "${prefs.designCapacityMah} mAh",
                        )
                    }
                    item {
                        MetricZh(
                            stringResource(R.string.battery_health_pct),
                            h?.healthPercent?.let { "$it%" }
                                ?: stringResource(R.string.battery_need_samples),
                        )
                    }
                    item {
                        MetricZh(
                            stringResource(R.string.battery_sample_count),
                            (h?.sampleCount ?: 0).toString(),
                        )
                    }
                    item {
                        Text(
                            stringResource(
                                R.string.battery_health_hint,
                                BatteryCapacityEstimator.MIN_PERCENT_DELTA,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                3 -> {
                    if (sessions.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.battery_history_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(sessions.take(40), key = { it.id }) { row ->
                            SessionCard(row)
                        }
                    }
                }
                else -> {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.battery_tracking_toggle),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = prefs.trackingEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        prefsStore.setTrackingEnabled(checked)
                                        if (!checked) {
                                            BatteryChargeService.stop(context.applicationContext)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.battery_alarm_toggle),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = prefs.chargeAlarmEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { prefsStore.setChargeAlarmEnabled(checked) }
                                },
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = alarmText,
                            onValueChange = { alarmText = it.filter { c -> c.isDigit() }.take(3) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.battery_alarm_percent)) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        R.string.battery_alarm_range,
                                        BatteryPrefs.MIN_ALARM,
                                        BatteryPrefs.MAX_ALARM,
                                    ),
                                )
                            },
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = designText,
                            onValueChange = { designText = it.filter { c -> c.isDigit() }.take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.battery_design_input)) },
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    alarmText.toIntOrNull()?.let {
                                        prefsStore.setChargeAlarmPercent(it)
                                    }
                                    designText.toIntOrNull()?.let {
                                        prefsStore.setDesignCapacityMah(it)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.battery_save_settings))
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(12.dp)) }
        }
    }
}

@Composable
private fun MetricZh(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SessionCard(row: BatterySessionEntity) {
    val fmt = remember {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }
    val title = stringResource(
        R.string.battery_session_title,
        row.startPercent,
        row.endPercent,
        fmt.format(Date(row.endedAt)),
    )
    val detail = if (row.estimatedFullMah > 0) {
        stringResource(R.string.battery_session_est, row.estimatedFullMah)
    } else {
        stringResource(
            R.string.battery_session_weak,
            BatteryCapacityEstimator.MIN_PERCENT_DELTA,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
