package com.onetools.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R

/**
 * 「实时动态实验室」：与「特色功能」分栏；此处只放实时动态类实验，不挂显示/切卡特色。
 */
@Composable
fun LiveLabScreen() {
    OneToolsPage(
        title = stringResource(R.string.lab_title),
        subtitle = stringResource(R.string.lab_subtitle),
    ) {
        item {
            OneToolsInlineNotice(
                text = stringResource(R.string.lab_scope_notice),
                danger = false,
            )
        }
        item {
            OneToolsSection(title = stringResource(R.string.lab_empty_title)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.lab_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
