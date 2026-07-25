package com.onetools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R

/**
 * Home hub: Caller + Meter on one page (tabbed), Battery stays separate.
 */
@Composable
fun CallerMeterHubScreen(
    onBack: () -> Unit,
    onOpenRecorder: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OneToolsToolHeader(
            title = stringResource(R.string.caller_meter_hub_title),
            onBack = onBack,
            subtitle = stringResource(R.string.caller_meter_hub_note),
        )
        Row(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text(stringResource(R.string.caller_title)) },
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text(stringResource(R.string.meter_title)) },
            )
        }
        when (tab) {
            0 -> CallerScreen(
                onBack = onBack,
                onOpenRecorder = onOpenRecorder,
                showBack = false,
            )
            else -> MeterScreen(
                onBack = onBack,
                showBack = false,
            )
        }
    }
}
