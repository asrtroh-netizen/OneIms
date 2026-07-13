package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneims.app.BuildConfig
import com.oneims.app.R

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    OneImsPage(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
    ) {
        item {
            SectionBlock(
                title = stringResource(R.string.appearance_title),
                description = stringResource(R.string.appearance_subtitle),
            ) {
                SettingsChoiceRow(
                    title = stringResource(R.string.theme_system),
                    subtitle = stringResource(R.string.theme_system_sub),
                    selected = state.themeMode == ThemeMode.SYSTEM,
                    onClick = { actions.onThemeModeChange(ThemeMode.SYSTEM) },
                )
                GroupDivider()
                SettingsChoiceRow(
                    title = stringResource(R.string.theme_light),
                    subtitle = stringResource(R.string.theme_light_sub),
                    selected = state.themeMode == ThemeMode.LIGHT,
                    onClick = { actions.onThemeModeChange(ThemeMode.LIGHT) },
                )
                GroupDivider()
                SettingsChoiceRow(
                    title = stringResource(R.string.theme_dark),
                    subtitle = stringResource(R.string.theme_dark_sub),
                    selected = state.themeMode == ThemeMode.DARK,
                    onClick = { actions.onThemeModeChange(ThemeMode.DARK) },
                )
                GroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.dynamic_color_title),
                    subtitle = stringResource(R.string.dynamic_color_sub),
                    checked = state.dynamicColor,
                    onCheckedChange = actions.onDynamicColorChange,
                    icon = Icons.Filled.Star,
                )
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.settings_tools_title),
                description = stringResource(R.string.settings_tools_subtitle),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.qs_tile_feature_title),
                    subtitle = stringResource(R.string.qs_tile_feature_desc),
                    onClick = actions.onOpenTileSettings,
                )
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.update_title),
                description = stringResource(
                    R.string.current_version,
                    BuildConfig.VERSION_NAME,
                ),
            ) {
                SettingsActionRow(
                    icon = Icons.Filled.Refresh,
                    title = if (state.checkingUpdate) {
                        stringResource(R.string.checking_update)
                    } else {
                        stringResource(R.string.action_check_update)
                    },
                    subtitle = stringResource(R.string.update_check_subtitle),
                    onClick = actions.onCheckUpdate,
                    enabled = !state.checkingUpdate,
                    trailingText = if (state.checkingUpdate) "…" else null,
                )
                state.updateInfo?.let { info ->
                    GroupDivider()
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InlineNotice(
                            text = info.message,
                            danger = !info.hasUpdate && info.latestVersion.isBlank(),
                        )
                        if (info.hasUpdate && info.downloadUrl.isNotBlank()) {
                            if (info.releaseNotes.isNotBlank()) {
                                Text(
                                    info.releaseNotes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 8,
                                )
                            }
                            OneImsPrimaryButton(
                                text = stringResource(R.string.action_download_install),
                                onClick = { actions.onDownloadUpdate(info) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        item {
            // 「关于」只保留可核对的事实性条目；赞赏已独立为底栏「赞助」分页。
            SectionBlock(title = stringResource(R.string.about_title)) {
                SettingsActionRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                    ),
                    onClick = null,
                    trailingText = BuildConfig.VERSION_NAME,
                )
                GroupDivider()
                SettingsActionRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.about_author),
                    subtitle = stringResource(R.string.about_author_name),
                    onClick = null,
                )
            }
        }
    }
}
