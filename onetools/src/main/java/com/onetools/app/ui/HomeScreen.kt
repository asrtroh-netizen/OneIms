package com.onetools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.channel.ChannelCardState

@Composable
fun HomeScreen(
    channelState: ChannelCardState,
    onActivateOrCheck: () -> Unit,
    onOpenCallerMeter: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenRecorder: () -> Unit,
    onExportDiag: () -> Unit,
) {
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
                        text = stringResource(R.string.home_section_tools),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_section_tools_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OneToolsInfoCard(
                    title = stringResource(R.string.caller_meter_card_title),
                    subtitle = stringResource(R.string.caller_meter_card_sub),
                    onClick = onOpenCallerMeter,
                )
                OneToolsInfoCard(
                    title = stringResource(R.string.battery_card_title),
                    subtitle = stringResource(R.string.battery_card_sub),
                    onClick = onOpenBattery,
                )
                OneToolsInfoCard(
                    title = stringResource(R.string.recorder_card_title),
                    subtitle = stringResource(R.string.recorder_card_sub),
                    onClick = onOpenRecorder,
                )
                OneToolsInfoCard(
                    title = stringResource(R.string.updates_card_title),
                    subtitle = stringResource(R.string.updates_card_sub),
                    onClick = onOpenUpdates,
                )
                OneToolsInfoCard(
                    title = stringResource(R.string.export_card_title),
                    subtitle = stringResource(R.string.export_card_sub),
                    onClick = onExportDiag,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.relation_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
