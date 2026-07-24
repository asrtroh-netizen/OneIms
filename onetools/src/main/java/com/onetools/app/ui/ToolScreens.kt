package com.onetools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.battery.BatteryReader
import com.onetools.app.battery.BatterySnapshot
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var snap by remember { mutableStateOf<BatterySnapshot?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = BatteryReader.read(context)
            delay(2000)
        }
    }
    ToolScaffold(
        title = stringResource(R.string.battery_title),
        onBack = onBack,
    ) {
        val s = snap
        if (s == null) {
            item { Text(stringResource(R.string.battery_unavailable)) }
        } else {
            item { MetricRow("Level", "${s.percent}%") }
            item { MetricRow("Health", s.healthLabel) }
            item { MetricRow("Temperature", "%.1f °C".format(s.temperatureC)) }
            item { MetricRow("Voltage", "${s.voltageMv} mV") }
            item { MetricRow("Status", s.statusLabel) }
            item { MetricRow("Power", s.pluggedLabel) }
            item { MetricRow("Technology", s.technology) }
            item {
                MetricRow(
                    "Cycle count",
                    if (s.cycleCount >= 0) s.cycleCount.toString() else "N/A (need API 34+)",
                )
            }
            item {
                MetricRow(
                    "Charge counter",
                    if (s.chargeCounterMah >= 0) "${s.chargeCounterMah} mAh" else "N/A",
                )
            }
            item { MetricRow("Current", "${s.currentNowMa} mA") }
            item {
                val remain = s.chargingTimeRemainingMs
                MetricRow(
                    "Charge remaining",
                    if (remain > 0) "${remain / 60_000} min" else "N/A",
                )
            }
            item {
                Text(
                    stringResource(R.string.battery_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) {
            Text("← $title")
        }
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

@Composable
private fun MetricRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
