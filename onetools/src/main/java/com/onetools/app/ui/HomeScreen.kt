package com.onetools.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.channel.ChannelCardState

@Composable
fun HomeScreen(
    channelState: ChannelCardState,
    onActivateOrCheck: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenMeter: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenRecorder: () -> Unit,
    onOpenCaller: () -> Unit,
    onOpenTelo: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 28.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            StatusHero(
                state = channelState,
                onPrimaryAction = onActivateOrCheck,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.battery_card_title),
                subtitle = stringResource(R.string.battery_card_sub),
                onClick = onOpenBattery,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.meter_card_title),
                subtitle = stringResource(R.string.meter_card_sub),
                onClick = onOpenMeter,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.caller_card_title),
                subtitle = stringResource(R.string.caller_card_sub),
                onClick = onOpenCaller,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.recorder_card_title),
                subtitle = stringResource(R.string.recorder_card_sub),
                onClick = onOpenRecorder,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.telo_card_title),
                subtitle = stringResource(R.string.telo_card_sub),
                onClick = onOpenTelo,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.updates_card_title),
                subtitle = stringResource(R.string.updates_card_sub),
                onClick = onOpenUpdates,
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.export_card_title),
                subtitle = stringResource(R.string.export_card_sub),
            )
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

@Composable
private fun InfoCard(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
