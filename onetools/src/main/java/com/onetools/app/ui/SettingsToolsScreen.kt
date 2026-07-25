package com.onetools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onetools.app.R

@Composable
fun SettingsToolsScreen(
    onExportDiag: () -> Unit,
) {
    OneToolsPage(
        title = stringResource(R.string.tab_settings),
        subtitle = stringResource(R.string.settings_subtitle),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.home_section_tools),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                OneToolsInfoCard(
                    title = stringResource(R.string.export_card_title),
                    subtitle = stringResource(R.string.export_card_sub),
                    onClick = onExportDiag,
                )
            }
        }
    }
}
