package com.onebattery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.onebattery.app.R
import com.onebattery.app.battery.BatteryAppDrainStore
import com.onebattery.app.battery.BatteryCapacityEstimator
import com.onebattery.app.battery.BatteryChargeService
import com.onebattery.app.battery.BatteryHealthSummary
import com.onebattery.app.battery.BatteryPrefs
import com.onebattery.app.battery.BatteryPrefsSnapshot
import com.onebattery.app.battery.BatteryReader
import com.onebattery.app.battery.BatterySampleEntity
import com.onebattery.app.battery.BatterySessionEntity
import com.onebattery.app.battery.BatterySessionStore
import com.onebattery.app.battery.BatterySnapshot
import com.onebattery.app.battery.BatteryStatsDumpParser
import com.onebattery.app.battery.BatteryStatsShizuku
import com.onebattery.app.battery.PixelDesignCapacity
import com.onebattery.app.battery.healthSummary
import com.onebattery.app.meter.UsageAccess
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    onBack: () -> Unit = {},
    showBack: Boolean = true,
) {
    val context = LocalContext.current
    val prefsStore = remember { BatteryPrefs(context.applicationContext) }
    val sessionStore = remember { BatterySessionStore(context.applicationContext) }
    val drainStore = remember { BatteryAppDrainStore(context.applicationContext) }
    val prefs by prefsStore.snapshotFlow.collectAsState(
        initial = BatteryPrefsSnapshot(
            designCapacityMah = BatteryPrefs.DEFAULT_DESIGN_MAH,
            chargeAlarmEnabled = true,
            chargeAlarmPercent = BatteryPrefs.DEFAULT_ALARM_PERCENT,
            lowAlarmEnabled = true,
            lowAlarmPercent = BatteryPrefs.DEFAULT_LOW_ALARM_PERCENT,
            tempHighAlarmEnabled = true,
            tempHighC = BatteryPrefs.DEFAULT_TEMP_HIGH_C,
            tempLowAlarmEnabled = false,
            tempLowC = BatteryPrefs.DEFAULT_TEMP_LOW_C,
            trackingEnabled = true,
            persistentNotifEnabled = true,
            designCapacityUserSet = false,
        ),
    )
    val sessions by sessionStore.sessions.collectAsState(initial = emptyList())
    val drainRows by drainStore.observeToday().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var snap by remember { mutableStateOf<BatterySnapshot?>(null) }
    var health by remember { mutableStateOf<BatteryHealthSummary?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var designText by remember { mutableStateOf("") }
    var alarmText by remember { mutableStateOf("") }
    var lowAlarmText by remember { mutableStateOf("") }
    var tempHighText by remember { mutableStateOf("") }
    var tempLowText by remember { mutableStateOf("") }
    var hasUsage by remember { mutableStateOf(UsageAccess.hasPermission(context)) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var curveSamples by remember { mutableStateOf<List<BatterySampleEntity>>(emptyList()) }
    var wakeLocks by remember { mutableStateOf<List<BatteryStatsDumpParser.WakeLockRow>>(emptyList()) }
    var statsHint by remember { mutableStateOf<String?>(null) }
    var statsError by remember { mutableStateOf<String?>(null) }
    var statsBusy by remember { mutableStateOf(false) }
    var statsVia by remember { mutableStateOf<String?>(null) }
    var designHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefs.trackingEnabled, prefs.persistentNotifEnabled, prefs.designCapacityMah) {
        while (true) {
            snap = BatteryReader.read(context)
            health = sessionStore.healthSummary(prefs.designCapacityMah)
            BatteryChargeService.sync(context.applicationContext)
            delay(2000)
        }
    }

    LaunchedEffect(
        prefs.designCapacityMah,
        prefs.chargeAlarmPercent,
        prefs.lowAlarmPercent,
        prefs.tempHighC,
        prefs.tempLowC,
    ) {
        designText = prefs.designCapacityMah.toString()
        alarmText = prefs.chargeAlarmPercent.toString()
        lowAlarmText = prefs.lowAlarmPercent.toString()
        tempHighText = prefs.tempHighC.toInt().toString()
        tempLowText = prefs.tempLowC.toInt().toString()
    }

    LaunchedEffect(selectedSessionId) {
        val id = selectedSessionId
        curveSamples = if (id == null) emptyList() else sessionStore.samplesFor(id)
    }

    val discharge = sessions.filter { it.kind == "DISCHARGE" }
    val charge = sessions.filter { it.kind == "CHARGE" }

    val body: LazyListScope.() -> Unit = {
            if (!showBack) {
                item {
                    Text(
                        stringResource(R.string.page_onebattery),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.onebattery_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.battery_accu_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        stringResource(R.string.battery_tab_live),
                        stringResource(R.string.battery_tab_drain),
                        stringResource(R.string.battery_tab_health),
                        stringResource(R.string.battery_tab_history),
                        stringResource(R.string.battery_tab_stats),
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
                        item { MetricZh(stringResource(R.string.battery_current), "${s.currentNowMa} mA") }
                        item {
                            MetricZh(
                                stringResource(R.string.battery_watts),
                                stringResource(R.string.battery_watts_value, s.powerWatts),
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
                            )
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
                }
                3 -> {
                    item {
                        Text(
                            stringResource(R.string.battery_history_discharge),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item { Text(stringResource(R.string.battery_history_pick)) }
                    if (curveSamples.size >= 2) {
                        item {
                            BatterySparkline(percents = curveSamples.map { it.percent })
                        }
                    }
                    if (discharge.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.battery_history_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(discharge.take(20), key = { it.id }) { row ->
                            SessionCard(
                                row = row,
                                selected = row.id == selectedSessionId,
                                onClick = { selectedSessionId = row.id },
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.battery_history_charge),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(charge.take(20), key = { "c-${it.id}" }) { row ->
                        SessionCard(row = row, selected = false, onClick = null)
                    }
                }
                4 -> {
                    item {
                        Text(
                            stringResource(R.string.battery_stats_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!BatteryStatsShizuku.isReady()) {
                        item {
                            Text(
                                stringResource(R.string.battery_stats_need_shizuku),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    statsBusy = true
                                    statsError = null
                                    val dump = withContext(Dispatchers.IO) {
                                        BatteryStatsShizuku.dumpBatteryStats(context.applicationContext)
                                    }
                                    statsBusy = false
                                    if (!dump.ok) {
                                        statsError = dump.error
                                        wakeLocks = emptyList()
                                        statsHint = null
                                        statsVia = null
                                    } else {
                                        wakeLocks = BatteryStatsDumpParser.parseWakeLocks(dump.text)
                                        statsHint = BatteryStatsDumpParser.parseDeepSleepHint(dump.text)
                                        statsVia = dump.via
                                    }
                                }
                            },
                            enabled = !statsBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.battery_stats_fetch))
                        }
                    }
                    statsError?.let { err ->
                        item {
                            Text(
                                stringResource(R.string.battery_stats_error, err),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    statsVia?.let { via ->
                        item {
                            Text(stringResource(R.string.battery_stats_via, via))
                        }
                    }
                    statsHint?.let { hint ->
                        item {
                            Text(stringResource(R.string.battery_stats_deep_hint, hint))
                        }
                    }
                    if (wakeLocks.isEmpty() && statsError == null) {
                        item {
                            Text(
                                stringResource(R.string.battery_stats_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(wakeLocks, key = { it.name + it.detail.hashCode() }) { row ->
                            Text(row.detail, style = MaterialTheme.typography.bodySmall)
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
                                        BatteryChargeService.sync(context.applicationContext)
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
                                stringResource(R.string.battery_persistent_notif_toggle) +
                                    "\n" +
                                    stringResource(R.string.battery_persistent_notif_hint),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = prefs.persistentNotifEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        prefsStore.setPersistentNotifEnabled(checked)
                                        BatteryChargeService.sync(context.applicationContext)
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
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.battery_low_alarm_toggle),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = prefs.lowAlarmEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { prefsStore.setLowAlarmEnabled(checked) }
                                },
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = lowAlarmText,
                            onValueChange = { lowAlarmText = it.filter { c -> c.isDigit() }.take(3) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.battery_low_alarm_percent)) },
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.battery_temp_high_alarm_toggle),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = prefs.tempHighAlarmEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { prefsStore.setTempHighAlarmEnabled(checked) }
                                },
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = tempHighText,
                            onValueChange = {
                                tempHighText = it.filter { c -> c.isDigit() || c == '-' }.take(3)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.battery_temp_high_c)) },
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.battery_temp_low_alarm_toggle),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = prefs.tempLowAlarmEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { prefsStore.setTempLowAlarmEnabled(checked) }
                                },
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = tempLowText,
                            onValueChange = {
                                tempLowText = it.filter { c -> c.isDigit() || c == '-' }.take(3)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.battery_temp_low_c)) },
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
                        Text(
                            stringResource(R.string.battery_design_presets),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.battery_design_auto_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    val preset = prefsStore.applyDetectedPixelPreset()
                                    if (preset != null) {
                                        designText = preset.mah.toString()
                                        designHint = context.getString(
                                            R.string.battery_design_detect_ok,
                                            preset.label,
                                            preset.mah,
                                        )
                                    } else {
                                        designHint = context.getString(R.string.battery_design_detect_fail)
                                        Toast.makeText(
                                            context,
                                            R.string.battery_design_detect_fail,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.battery_design_detect))
                        }
                    }
                    designHint?.let { hint ->
                        item {
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PixelDesignCapacity.PRESETS.forEach { preset ->
                                val selected = designHint?.contains(preset.label) == true ||
                                    (designHint == null && prefs.designCapacityMah == preset.mah &&
                                        PixelDesignCapacity.match()?.label == preset.label)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        scope.launch {
                                            prefsStore.setDesignCapacityMah(preset.mah, userSet = true)
                                            designText = preset.mah.toString()
                                            designHint = context.getString(
                                                R.string.battery_design_detect_ok,
                                                preset.label,
                                                preset.mah,
                                            )
                                        }
                                    },
                                    label = { Text("${preset.label} ? ${preset.mah}") },
                                )
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    alarmText.toIntOrNull()?.let { prefsStore.setChargeAlarmPercent(it) }
                                    lowAlarmText.toIntOrNull()?.let { prefsStore.setLowAlarmPercent(it) }
                                    tempHighText.toFloatOrNull()?.let { prefsStore.setTempHighC(it) }
                                    tempLowText.toFloatOrNull()?.let { prefsStore.setTempLowC(it) }
                                    designText.toIntOrNull()?.let {
                                        prefsStore.setDesignCapacityMah(it, userSet = true)
                                    }
                                    BatteryChargeService.sync(context.applicationContext)
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

    if (showBack) {
        OneBatteryToolPage(
            title = stringResource(R.string.page_onebattery),
            onBack = onBack,
            verticalSpacing = 10,
            content = body,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = body,
        )
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
private fun SessionCard(
    row: BatterySessionEntity,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val title = stringResource(
        R.string.battery_session_title,
        row.startPercent,
        row.endPercent,
        fmt.format(Date(row.endedAt)),
    )
    val detail = when {
        row.kind == "DISCHARGE" && row.deepSleepPercent >= 0 ->
            stringResource(
                R.string.battery_history_deep_sleep,
                row.deepSleepPercent,
                (row.screenOffMs / 60_000L).toInt(),
            )
        row.estimatedFullMah > 0 ->
            stringResource(R.string.battery_session_est, row.estimatedFullMah)
        else ->
            stringResource(
                R.string.battery_session_weak,
                BatteryCapacityEstimator.MIN_PERCENT_DELTA,
            )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(
            title + if (selected) " *" else "",
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
