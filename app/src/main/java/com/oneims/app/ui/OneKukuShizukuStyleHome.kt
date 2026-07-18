package com.oneims.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import androidx.compose.material3.Button

/**
 * 独立版首页：对齐邻仓新作 Shizuku 的「两态状态卡 + 2×2 四小方块」。
 * 品牌文案为 OneKuku；不承载运营商推荐 / 保存恢复 / 设备卡。
 */
private enum class HeroVisual { INACTIVE, ACTIVATING, READY }

@Composable
fun OneKukuShizukuStyleStatusHero(
    oneKukuState: OneKukuCardState,
    detailOverride: String?,
    onClick: () -> Unit,
    onOpenDeviceDetails: (() -> Unit)? = null,
) {
    val hero = when (oneKukuState) {
        OneKukuCardState.READY,
        OneKukuCardState.SLEEPING,
        -> HeroVisual.READY
        OneKukuCardState.ACTIVATING -> HeroVisual.ACTIVATING
        OneKukuCardState.INACTIVE -> HeroVisual.INACTIVE
    }
    val containerColor = when (hero) {
        HeroVisual.INACTIVE -> ShizukuHomePalette.heroInactiveBg
        HeroVisual.ACTIVATING -> ShizukuHomePalette.heroActivatingBg
        HeroVisual.READY -> ShizukuHomePalette.heroReadyBg
    }
    val contentColor = when (hero) {
        HeroVisual.INACTIVE -> ShizukuHomePalette.heroInactiveFg
        HeroVisual.ACTIVATING -> ShizukuHomePalette.heroActivatingFg
        HeroVisual.READY -> ShizukuHomePalette.heroReadyFg
    }
    val title = when (hero) {
        HeroVisual.READY -> stringResource(R.string.channel_display_name)
        HeroVisual.ACTIVATING -> stringResource(R.string.onekuku_title_activating)
        HeroVisual.INACTIVE -> stringResource(R.string.onekuku_home_hero_title_inactive)
    }
    val pill = when (hero) {
        HeroVisual.READY -> stringResource(R.string.onekuku_pill_ready)
        HeroVisual.ACTIVATING -> stringResource(R.string.onekuku_pill_activating)
        HeroVisual.INACTIVE -> stringResource(R.string.onekuku_pill_inactive)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = if (hero == HeroVisual.READY) 2.dp else 0.dp,
        shadowElevation = if (hero == HeroVisual.READY) 1.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(
                    imageVector = when (hero) {
                        HeroVisual.INACTIVE -> Icons.Filled.Warning
                        HeroVisual.ACTIVATING -> Icons.Filled.PlayArrow
                        HeroVisual.READY -> Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = contentColor,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onekuku_home_hero_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = contentColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = pill,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                    if (hero != HeroVisual.READY) {
                        Text(
                            text = when (hero) {
                                HeroVisual.ACTIVATING ->
                                    stringResource(R.string.onekuku_subtitle_activating)
                                else ->
                                    stringResource(R.string.onekuku_home_hero_subtitle_inactive)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                        )
                        Text(
                            text = detailOverride ?: when (hero) {
                                HeroVisual.ACTIVATING ->
                                    stringResource(R.string.onekuku_detail_activating)
                                else ->
                                    stringResource(R.string.onekuku_home_hero_detail_inactive)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.78f),
                        )
                    }
                }
                if (onOpenDeviceDetails != null) {
                    Surface(
                        onClick = onOpenDeviceDetails,
                        shape = RoundedCornerShape(percent = 50),
                        color = contentColor.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = stringResource(R.string.home_device_details),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.88f),
                            maxLines = 1,
                        )
                    }
                }
            }
            TwoStageStrip(
                ready = hero == HeroVisual.READY,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun TwoStageStrip(
    ready: Boolean,
    contentColor: Color,
) {
    val litCount = if (ready) 2 else 1
    val labels = listOf(
        stringResource(R.string.onekuku_home_hero_stage_inactive),
        stringResource(R.string.onekuku_home_hero_stage_ready),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val lit = index < litCount
            val stageColor = if (lit) contentColor else contentColor.copy(alpha = 0.32f)
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (litCount > 1) contentColor.copy(alpha = 0.55f)
                            else contentColor.copy(alpha = 0.18f),
                        ),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = stageColor, shape = CircleShape),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = stageColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 对齐新作 Shizuku「无线调试启动」卡：标题 + 指南 / 配对 / 启动。
 * 插在状态卡与四小方块之间。
 */
@Composable
fun OneKukuWirelessStartCard(
    startEnabled: Boolean,
    onGuide: () -> Unit,
    onPair: () -> Unit,
    onStart: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = ShizukuHomePalette.tileSurface(),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = ShizukuHomePalette.tileContent(),
                )
                Text(
                    text = stringResource(R.string.onekuku_home_wireless_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ShizukuHomePalette.tileContent(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onGuide) {
                    Text(stringResource(R.string.onekuku_home_wireless_guide))
                }
                OutlinedButton(onClick = onPair) {
                    Text(stringResource(R.string.onekuku_home_wireless_pair))
                }
                Button(
                    onClick = onStart,
                    enabled = startEnabled,
                ) {
                    Text(stringResource(R.string.onekuku_home_wireless_start))
                }
            }
        }
    }
}

@Composable
fun OneKukuShizukuStyleQuickGrid(
    channelReady: Boolean,
    onApps: () -> Unit,
    onTerminal: () -> Unit,
    onRoot: () -> Unit,
    onAdb: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickTile(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.onekuku_home_tile_apps),
                subtitle = stringResource(
                    if (channelReady) R.string.onekuku_home_tile_apps_sub_ready
                    else R.string.onekuku_home_tile_apps_sub_waiting,
                ),
                dimmed = !channelReady,
                onClick = onApps,
                modifier = Modifier.weight(1f),
            )
            QuickTile(
                icon = Icons.Filled.Build,
                title = stringResource(R.string.onekuku_home_tile_terminal),
                subtitle = stringResource(R.string.onekuku_home_tile_terminal_sub),
                dimmed = !channelReady,
                onClick = onTerminal,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickTile(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.onekuku_home_tile_root),
                subtitle = stringResource(R.string.onekuku_home_tile_root_sub),
                dimmed = true,
                onClick = onRoot,
                modifier = Modifier.weight(1f),
            )
            QuickTile(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.onekuku_home_tile_adb),
                subtitle = stringResource(R.string.onekuku_home_tile_adb_sub),
                dimmed = false,
                onClick = onAdb,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .alpha(if (dimmed) 0.45f else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = ShizukuHomePalette.tileSurface(),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = ShizukuHomePalette.tileContent(),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ShizukuHomePalette.tileContent(),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ShizukuHomePalette.tileContent().copy(alpha = 0.78f),
            )
        }
    }
}
