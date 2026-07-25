package com.onetools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.channel.ChannelCardState
import com.onetools.app.meter.MeterOverlayController
import com.onetools.app.meter.MeterSettings
import com.onetools.app.meter.SpeedMonitorService
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    channelState: ChannelCardState,
    onActivateOrCheck: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meterSettings = remember { MeterSettings(context.applicationContext) }
    val meterSnap by meterSettings.snapshotFlow.collectAsState(
        initial = com.onetools.app.meter.MeterPrefsSnapshot(),
    )
    var meterRunning by remember { mutableStateOf(SpeedMonitorService.isRunning) }

    OneToolsPage(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.home_subtitle),
    ) {
        item {
            StatusHero(
                state = channelState,
                onPrimaryAction = onActivateOrCheck,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_quick_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.home_quick_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OneToolsSettingsGroup {
                    HomeToggleRow(
                        title = stringResource(R.string.home_toggle_meter),
                        checked = meterRunning,
                        onCheckedChange = { on ->
                            if (on) SpeedMonitorService.start(context) else SpeedMonitorService.stop(context)
                            meterRunning = on
                        },
                    )
                    HomeToggleRow(
                        title = stringResource(R.string.home_toggle_overlay),
                        checked = meterSnap.overlayEnabled && meterRunning,
                        onCheckedChange = { on ->
                            if (on && !MeterOverlayController(context).canDraw()) {
                                Toast.makeText(context, R.string.recorder_need_overlay, Toast.LENGTH_LONG).show()
                                return@HomeToggleRow
                            }
                            scope.launch {
                                meterSettings.setOverlayEnabled(on)
                                if (on && !SpeedMonitorService.isRunning) {
                                    SpeedMonitorService.start(context)
                                    meterRunning = true
                                }
                                context.startService(
                                    android.content.Intent(
                                        context,
                                        SpeedMonitorService::class.java,
                                    ).setAction(SpeedMonitorService.ACTION_APPLY_PREFS),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
