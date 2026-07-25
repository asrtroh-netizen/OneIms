package com.onetools.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.channel.ChannelCardPolicy
import com.onetools.app.channel.ChannelCardState

/**
 * 对齐 OneIMS `StatusHero` 视觉与四态交互（R0 受控移植精简版）。
 */
@Composable
fun StatusHero(
    state: ChannelCardState,
    onPrimaryAction: () -> Unit,
    onOpenDeviceDetails: (() -> Unit)? = null,
) {
    val alert = ChannelCardPolicy.isAlert(state)
    val busy = ChannelCardPolicy.isBusy(state)
    val settled = ChannelCardPolicy.isSettled(state)
    val readyLook = !alert
    val containerColor = when {
        alert -> MaterialTheme.colorScheme.errorContainer
        busy -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.White
    }
    val contentColor = when {
        alert -> MaterialTheme.colorScheme.onErrorContainer
        busy -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> Color(0xFF1A1B20)
    }

    val title = when (state) {
        ChannelCardState.READY,
        ChannelCardState.SLEEPING,
        -> stringResource(R.string.channel_display_name)
        ChannelCardState.INACTIVE -> stringResource(R.string.title_inactive)
        ChannelCardState.ACTIVATING -> stringResource(R.string.title_activating)
    }
    val subtitle = stringResource(
        when (state) {
            ChannelCardState.INACTIVE -> R.string.subtitle_inactive
            ChannelCardState.ACTIVATING -> R.string.subtitle_activating
            ChannelCardState.READY -> R.string.subtitle_ready
            ChannelCardState.SLEEPING -> R.string.subtitle_sleeping
        },
    )
    val detail = stringResource(
        when (state) {
            ChannelCardState.INACTIVE -> R.string.detail_inactive
            ChannelCardState.ACTIVATING -> R.string.detail_activating
            ChannelCardState.READY -> R.string.detail_ready
            ChannelCardState.SLEEPING -> R.string.detail_sleeping
        },
    )
    val statusPill = stringResource(
        when (state) {
            ChannelCardState.INACTIVE -> R.string.pill_inactive
            ChannelCardState.ACTIVATING -> R.string.pill_activating
            ChannelCardState.READY -> R.string.pill_ready
            ChannelCardState.SLEEPING -> R.string.pill_sleeping
        },
    )
    val actionLabel = stringResource(
        when (state) {
            ChannelCardState.INACTIVE -> R.string.action_activate
            ChannelCardState.READY,
            ChannelCardState.SLEEPING,
            -> R.string.action_check
            ChannelCardState.ACTIVATING -> R.string.action_activating
        },
    )
    val actionSub = when (state) {
        ChannelCardState.INACTIVE -> stringResource(R.string.action_activate_sub)
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = if (readyLook && !busy) 2.dp else 0.dp,
        shadowElevation = if (readyLook && !busy) 1.dp else 0.dp,
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
                    imageVector = when {
                        alert -> Icons.Filled.Warning
                        busy -> Icons.Filled.Refresh
                        else -> Icons.Filled.CheckCircle
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
                        text = stringResource(R.string.channel_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = contentColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = statusPill,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                    if (!settled) {
                        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = contentColor)
                        Text(
                            detail,
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
                            text = stringResource(R.string.device_details),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.88f),
                            maxLines = 1,
                        )
                    }
                }
            }

            StageProgress(
                litCount = ChannelCardPolicy.litStageCount(state),
                contentColor = contentColor,
            )

            if (!settled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryPillButton(
                        text = actionLabel,
                        onClick = onPrimaryAction,
                        enabled = !busy,
                        loading = busy,
                    )
                    if (actionSub != null) {
                        Text(
                            text = actionSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageProgress(litCount: Int, contentColor: Color) {
    val stages = listOf(
        R.string.stage_inactive,
        R.string.stage_activate,
        R.string.stage_ready,
        R.string.stage_sleeping,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEachIndexed { index, labelRes ->
            val lit = index < litCount
            val stageColor = if (lit) contentColor else contentColor.copy(alpha = 0.32f)
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            color = if (index < litCount) {
                                contentColor.copy(alpha = 0.55f)
                            } else {
                                contentColor.copy(alpha = 0.18f)
                            },
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
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = stageColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OneToolsPrimaryButton(
        text = if (loading) stringResource(R.string.action_processing) else text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
    )
}
