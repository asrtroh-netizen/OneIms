package com.oneims.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import com.oneims.app.core.formatCarrierShortName
import com.oneims.app.model.SimInfo
import com.oneims.app.ui.theme.OneImsTokens

private val PageMaxWidth = 960.dp
private val RailBreakpoint = 720.dp
private val OneImsBrandRed = Color(0xFFD6242F)
private val DockIslandShape = RoundedCornerShape(28.dp)

@Composable
fun OneImsScaffold(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    busyLabel: String?,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= RailBreakpoint

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    // 悬浮圆角岛 Dock；选中高亮改为圆形，避免六项时胶囊指示器挤成一团。
                    val dockColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = DockIslandShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            tonalElevation = 4.dp,
                            shadowElevation = 6.dp,
                        ) {
                            NavigationBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .clip(DockIslandShape),
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                            ) {
                                AppDestination.entries.forEach { destination ->
                                    val selected = destination == selectedDestination
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { onDestinationSelected(destination) },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                                        } else {
                                                            Color.Transparent
                                                        },
                                                        shape = CircleShape,
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = destination.icon,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(destination.labelRes),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        alwaysShowLabel = true,
                                        colors = dockColors,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        header = { OneImsMark() },
                    ) {
                        AppDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = destination == selectedDestination,
                                onClick = { onDestinationSelected(destination) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    content()
                    if (busyLabel != null) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .semantics { contentDescription = busyLabel },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneImsMark() {
    Surface(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = OneImsBrandRed,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
fun OneImsPage(
    title: String,
    subtitle: String,
    sims: List<SimInfo> = emptyList(),
    selectedSubId: Int = -1,
    onSelectSim: ((Int) -> Unit)? = null,
    simSelectionEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = PageMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 28.dp,
                end = 20.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (sims.isNotEmpty() && onSelectSim != null) {
                        SelectedSimPill(
                            sims = sims,
                            selectedSubId = selectedSubId,
                            onSelectSim = onSelectSim,
                            enabled = simSelectionEnabled,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun SectionBlock(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingsGroup(content = content)
    }
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneImsTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = { content() })
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    )
}

/**
 * OneIms 主确认动作的统一样式。普通单选、Tab、筛选胶囊与次要按钮不得复用此组件，
 * 避免把页面层级全部抹平成同一种强调级别。
 */
@Composable
fun OneImsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = stringResource(R.string.action_processing),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.38f),
            disabledContentColor = Color.Black.copy(alpha = 0.38f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.Black,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = if (loading) loadingText else text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OneImsTokens.rowMinHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = ripple(color = OneImsTokens.pressedOverlay()),
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = OneImsTokens.cardPaddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            LeadingIcon(icon)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = ripple(color = OneImsTokens.pressedOverlay()),
                onClick = onClick,
            )
            .padding(horizontal = OneImsTokens.cardPaddingHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.38f,
                ),
            )
        }
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingText: String? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interaction = if (onClick != null) {
        Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = ripple(color = OneImsTokens.pressedOverlay()),
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OneImsTokens.rowMinHeight)
            .then(interaction)
            .padding(horizontal = OneImsTokens.cardPaddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LeadingIcon(
            icon = icon,
            containerColor = iconContainerColor,
            contentColor = iconColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LeadingIcon(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(OneImsTokens.iconSize),
                tint = contentColor,
            )
        }
    }
}

data class ActionSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
)

@Composable
fun ActionGrid(actions: List<ActionSpec>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 760.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.chunked(columns).forEach { rowActions ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowActions.forEach { action ->
                        ActionTile(
                            action = action,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(columns - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    action: ActionSpec,
    modifier: Modifier = Modifier,
) {
    val container = if (action.danger) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (action.danger) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .heightIn(min = 124.dp)
            .clickable(
                enabled = action.enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(color = OneImsTokens.pressedOverlay()),
                onClick = action.onClick,
            ),
        shape = RoundedCornerShape(OneImsTokens.cardCornerRadius),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = if (action.enabled) contentColor else contentColor.copy(alpha = 0.38f),
            )
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (action.enabled) contentColor else contentColor.copy(alpha = 0.38f),
            )
            Text(
                text = action.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (action.enabled) contentColor.copy(alpha = 0.78f)
                else contentColor.copy(alpha = 0.38f),
            )
        }
    }
}

/**
 * 首页顶部 OneKuku 总控卡：保留原 Hero 外壳（extraLarge 圆角、未激活红 / 已激活白、左图标），
 * 对内换成 OneKuku 四态文案、右上状态胶囊、轻量进度阶段与主操作按钮；
 * 「一键恢复通话」仅在首页底部应急区出现，本卡不重复该入口。
 * 不展示终端、命令或底层通道名称；切卡改由顶栏选卡胶囊负责。
 */
@Composable
fun StatusHero(
    oneKukuState: OneKukuCardState,
    onPrimaryAction: () -> Unit,
    onOpenDeviceDetails: (() -> Unit)? = null,
    detailOverride: String? = null,
) {
    val alert = OneKukuCardPolicy.isAlert(oneKukuState)
    val busy = OneKukuCardPolicy.isBusy(oneKukuState)
    val ready = !alert
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

    val title = stringResource(
        when (oneKukuState) {
            OneKukuCardState.INACTIVE -> R.string.onekuku_title_inactive
            OneKukuCardState.ACTIVATING -> R.string.onekuku_title_activating
            OneKukuCardState.READY -> R.string.onekuku_title_ready
            OneKukuCardState.EXECUTING -> R.string.onekuku_title_running
            OneKukuCardState.FAILED -> R.string.onekuku_title_failed
        },
    )
    val subtitle = stringResource(
        when (oneKukuState) {
            OneKukuCardState.INACTIVE -> R.string.onekuku_subtitle_inactive
            OneKukuCardState.ACTIVATING -> R.string.onekuku_subtitle_activating
            OneKukuCardState.READY -> R.string.onekuku_subtitle_ready
            OneKukuCardState.EXECUTING -> R.string.onekuku_subtitle_running
            OneKukuCardState.FAILED -> R.string.onekuku_subtitle_failed
        },
    )
    val detail = detailOverride ?: stringResource(
        when (oneKukuState) {
            OneKukuCardState.INACTIVE -> R.string.onekuku_detail_inactive
            OneKukuCardState.ACTIVATING -> R.string.onekuku_detail_activating
            OneKukuCardState.READY -> R.string.onekuku_detail_ready
            OneKukuCardState.EXECUTING -> R.string.onekuku_detail_running
            OneKukuCardState.FAILED -> R.string.onekuku_detail_failed
        },
    )
    val showResidentHint = oneKukuState == OneKukuCardState.READY
    val statusPill = stringResource(
        when (oneKukuState) {
            OneKukuCardState.INACTIVE -> R.string.onekuku_pill_inactive
            OneKukuCardState.ACTIVATING -> R.string.onekuku_pill_activating
            OneKukuCardState.READY -> R.string.onekuku_pill_ready
            OneKukuCardState.EXECUTING -> R.string.onekuku_pill_running
            OneKukuCardState.FAILED -> R.string.onekuku_pill_failed
        },
    )
    val actionLabel = stringResource(
        when (oneKukuState) {
            OneKukuCardState.INACTIVE,
            OneKukuCardState.FAILED,
            -> R.string.onekuku_action_activate
            OneKukuCardState.READY -> R.string.onekuku_action_check
            OneKukuCardState.ACTIVATING -> R.string.onekuku_action_activating
            OneKukuCardState.EXECUTING -> R.string.onekuku_action_running
        },
    )
    val actionSub = when (oneKukuState) {
        OneKukuCardState.INACTIVE,
        OneKukuCardState.FAILED,
        -> stringResource(R.string.onekuku_action_activate_sub)
        OneKukuCardState.READY -> stringResource(R.string.onekuku_action_check_sub)
        else -> null
    }
    val actionEnabled = !busy
    val actionLoading = busy

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = if (ready && !busy) 2.dp else 0.dp,
        shadowElevation = if (ready && !busy) 1.dp else 0.dp,
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
                        text = stringResource(R.string.onekuku_card_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                    if (showResidentHint) {
                        Text(
                            stringResource(R.string.onekuku_hint_resident),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.66f),
                        )
                    }
                    // 设备详情：与右上状态胶囊同款圆角胶囊，点击弹窗。
                    if (onOpenDeviceDetails != null) {
                        Surface(
                            onClick = onOpenDeviceDetails,
                            shape = RoundedCornerShape(percent = 50),
                            color = contentColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = stringResource(R.string.home_device_details),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = contentColor.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = statusPill,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }

            OneKukuStageProgress(
                litCount = OneKukuCardPolicy.litStageCount(oneKukuState),
                contentColor = contentColor,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OneImsPrimaryButton(
                    text = actionLabel,
                    onClick = onPrimaryAction,
                    enabled = actionEnabled,
                    loading = actionLoading,
                    // 激活中勿复用「正在恢复…」——划掉后台重开时用户会误以为在恢复配置。
                    loadingText = actionLabel,
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

/**
 * 首页逻辑状态卡：通话配置侧（可恢复 / 执行中），与下方 OneKuku 通道卡分离。
 */
@Composable
fun LogicStatusHero(
    oneKukuState: OneKukuCardState,
    hasSim: Boolean,
    onRestore: () -> Unit,
    onOpenDeviceDetails: (() -> Unit)? = null,
) {
    val restoring = oneKukuState == OneKukuCardState.EXECUTING
    val channelReady = oneKukuState == OneKukuCardState.READY
    val channelBusy = oneKukuState == OneKukuCardState.ACTIVATING
    val alert = !hasSim || (!channelReady && !restoring && !channelBusy)
    val busy = restoring || channelBusy
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
    val title = stringResource(
        when {
            !hasSim -> R.string.logic_title_no_sim
            restoring -> R.string.logic_title_restoring
            channelBusy -> R.string.logic_title_waiting_channel
            channelReady -> R.string.logic_title_ready
            else -> R.string.logic_title_need_channel
        },
    )
    val subtitle = stringResource(
        when {
            !hasSim -> R.string.logic_subtitle_no_sim
            restoring -> R.string.logic_subtitle_restoring
            channelBusy -> R.string.logic_subtitle_waiting_channel
            channelReady -> R.string.logic_subtitle_ready
            else -> R.string.logic_subtitle_need_channel
        },
    )
    val detail = stringResource(
        when {
            !hasSim -> R.string.logic_detail_no_sim
            restoring -> R.string.logic_detail_restoring
            channelBusy -> R.string.logic_detail_waiting_channel
            channelReady -> R.string.logic_detail_ready
            else -> R.string.logic_detail_need_channel
        },
    )
    val pill = stringResource(
        when {
            !hasSim -> R.string.logic_pill_no_sim
            restoring -> R.string.onekuku_pill_running
            channelBusy -> R.string.logic_pill_waiting
            channelReady -> R.string.logic_pill_ready
            else -> R.string.logic_pill_blocked
        },
    )
    val actionLabel = stringResource(
        if (restoring) R.string.onekuku_action_running else R.string.onekuku_action_restore,
    )
    val canRestore = hasSim && channelReady && !restoring

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = if (!alert && !busy) 2.dp else 0.dp,
        shadowElevation = if (!alert && !busy) 1.dp else 0.dp,
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
                        text = stringResource(R.string.logic_card_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                    if (onOpenDeviceDetails != null) {
                        Surface(
                            onClick = onOpenDeviceDetails,
                            shape = RoundedCornerShape(percent = 50),
                            color = contentColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = stringResource(R.string.home_device_details),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = contentColor.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = pill,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OneImsPrimaryButton(
                    text = actionLabel,
                    onClick = onRestore,
                    enabled = canRestore,
                    loading = restoring,
                    loadingText = actionLabel,
                )
                Text(
                    text = stringResource(
                        if (canRestore) {
                            R.string.onekuku_action_restore_sub
                        } else {
                            R.string.logic_action_restore_blocked_sub
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OneKukuStageProgress(
    litCount: Int,
    contentColor: Color,
) {
    // 旧四态疏朗布局：等分铺满 + 连线 weight(1f)，五态同样沿用，避免横向滚动挤成一团。
    val stages = OneKukuCardPolicy.stageLabelRes()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
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

/**
 * 右上角选卡胶囊：双卡时点击切换选中 SIM，选中项前置 6dp 圆点。
 */
@Composable
fun SelectedSimPill(
    sims: List<SimInfo>,
    selectedSubId: Int,
    onSelectSim: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (sims.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        sims.forEach { sim ->
            val selected = sim.subscriptionId == selectedSubId
            val shortName = formatCarrierShortName(sim.carrierName)
            val containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val interactionSource = remember(sim.subscriptionId) { MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .heightIn(min = OneImsTokens.simPillHeight, max = OneImsTokens.simPillHeight)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        indication = ripple(color = OneImsTokens.pressedOverlay()),
                        onClick = { onSelectSim(sim.subscriptionId) },
                    ),
                shape = RoundedCornerShape(percent = 50),
                color = containerColor,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(OneImsTokens.simPillDotSize)
                                .background(contentColor, CircleShape),
                        )
                    }
                    Text(
                        text = stringResource(R.string.sim_pill_label, sim.slotIndex + 1, shortName),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** @deprecated 使用 [SelectedSimPill] */
@Composable
fun SimCapsuleSwitcher(
    sims: List<SimInfo>,
    selectedSubId: Int,
    onSelectSim: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = SelectedSimPill(sims, selectedSubId, onSelectSim, modifier)

@Composable
fun InlineNotice(
    text: String,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val container = if (danger) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val content = if (danger) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (danger) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = content,
            )
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
        }
    }
}

