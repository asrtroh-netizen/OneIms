package com.onetools.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.onetools.app.R
import com.onetools.app.channel.ChannelCardState

@Composable
fun HomeScreen(
    channelState: ChannelCardState,
    onActivateOrCheck: () -> Unit,
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
            Text(
                text = stringResource(R.string.home_dock_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
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
