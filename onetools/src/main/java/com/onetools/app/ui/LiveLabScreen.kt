package com.onetools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onetools.app.R
import com.onetools.app.live.LiveStatusCapsuleOverlay
import com.onetools.app.live.LiveStatusHub
import com.onetools.app.live.LiveStatusPrefs
import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CameraAnchorResolver
import com.onetools.app.live.capsule.CapsuleGestureDefaults
import com.onetools.app.live.capsule.CapsuleGestureSlot
import kotlin.math.roundToInt

@Composable
fun LiveLabScreen() {
    val context = LocalContext.current
    val prefs = remember { LiveStatusPrefs(context) }
    var master by remember { mutableStateOf(prefs.masterEnabled) }
    var capsule by remember { mutableStateOf(prefs.capsuleEnabled) }
    var access by remember { mutableStateOf(LiveStatusHub.isNotificationAccessEnabled(context)) }
    var canOverlay by remember { mutableStateOf(LiveStatusCapsuleOverlay.get(context).canDraw()) }
    var sourceEnabled by remember {
        mutableStateOf(LiveStatusSource.entries.associateWith { prefs.isSourceEnabled(it) })
    }
    var preview by remember { mutableStateOf(LiveStatusHub.lastChipText()) }
    var widthScale by remember { mutableFloatStateOf(prefs.capsuleWidthScale) }
    var heightScale by remember { mutableFloatStateOf(prefs.capsuleHeightScale) }
    var offsetX by remember { mutableIntStateOf(prefs.capsuleOffsetXDp) }
    var offsetY by remember { mutableIntStateOf(prefs.capsuleOffsetYDp) }
    var exclusionCenter by remember {
        mutableStateOf(prefs.cameraExclusionMode == "CAMERA_CENTER")
    }
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColorEnabled) }
    var haptic by remember { mutableStateOf(prefs.hapticEnabled) }
    var cutoutX by remember { mutableIntStateOf(prefs.cutoutCalibXDp) }
    var cutoutY by remember { mutableIntStateOf(prefs.cutoutCalibYDp) }
    var cutoutGap by remember { mutableIntStateOf(prefs.cutoutGapPadDp) }
    var gestureEpoch by remember { mutableIntStateOf(0) }
    var showLayout by remember { mutableStateOf(false) }
    var showGestures by remember { mutableStateOf(false) }
    var showCutout by remember { mutableStateOf(false) }
    var showMoreDemos by remember { mutableStateOf(false) }
    val cutoutRaw = remember(cutoutX, cutoutY, gestureEpoch) {
        CameraAnchorResolver.resolveRaw(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val controlsEnabled = master && capsule

    fun applyCapsuleLayout() {
        LiveStatusCapsuleOverlay.get(context).applyLayoutFromPrefs()
    }

    fun ensureOverlayReady(): Boolean {
        if (!canOverlay) {
            LiveStatusHub.openOverlaySettings(context)
            return false
        }
        prefs.masterEnabled = true
        prefs.capsuleEnabled = true
        master = true
        capsule = true
        return true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = LiveStatusHub.isNotificationAccessEnabled(context)
                canOverlay = LiveStatusCapsuleOverlay.get(context).canDraw()
                preview = LiveStatusHub.lastChipText()
                master = prefs.masterEnabled
                capsule = prefs.capsuleEnabled
                widthScale = prefs.capsuleWidthScale
                heightScale = prefs.capsuleHeightScale
                offsetX = prefs.capsuleOffsetXDp
                offsetY = prefs.capsuleOffsetYDp
                exclusionCenter = prefs.cameraExclusionMode == "CAMERA_CENTER"
                dynamicColor = prefs.dynamicColorEnabled
                haptic = prefs.hapticEnabled
                cutoutX = prefs.cutoutCalibXDp
                cutoutY = prefs.cutoutCalibYDp
                cutoutGap = prefs.cutoutGapPadDp
                sourceEnabled = LiveStatusSource.entries.associateWith { prefs.isSourceEnabled(it) }
                LiveStatusHub.refreshCapsuleVisibility(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OneToolsPage(
        title = stringResource(R.string.lab_title),
        subtitle = stringResource(R.string.lab_subtitle),
    ) {
        item {
            OneToolsInlineNotice(
                text = if (access) {
                    stringResource(R.string.live_status_access_ready)
                } else {
                    stringResource(R.string.live_status_access_need)
                },
                danger = !access,
            )
        }
        item {
            OneToolsSection(
                title = stringResource(R.string.live_status_section_power),
                description = stringResource(R.string.live_status_section_power_desc),
            ) {
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_status_master),
                    subtitle = stringResource(R.string.live_status_master_sub),
                    checked = master,
                    onCheckedChange = {
                        master = it
                        prefs.masterEnabled = it
                        if (it) {
                            LiveStatusHub.refreshCapsuleVisibility(context)
                        } else {
                            LiveStatusHub.clear(context)
                        }
                        if (it && !access) {
                            Toast.makeText(context, R.string.live_status_access_need, Toast.LENGTH_LONG).show()
                        }
                    },
                )
                OneToolsGroupDivider()
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_status_capsule),
                    subtitle = stringResource(R.string.live_status_capsule_sub),
                    checked = capsule,
                    enabled = master,
                    onCheckedChange = {
                        capsule = it
                        prefs.capsuleEnabled = it
                        // 开岛时联锁打开总开关，避免「胶囊开着、Listener 仍被 master=false 短路」。
                        if (it && !master) {
                            master = true
                            prefs.masterEnabled = true
                        }
                        LiveStatusHub.refreshCapsuleVisibility(context)
                        if (it && !canOverlay) {
                            Toast.makeText(context, R.string.live_status_overlay_need, Toast.LENGTH_LONG).show()
                        }
                    },
                )
                if (!access || (capsule && !canOverlay)) {
                    OneToolsGroupDivider()
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!access) {
                            OneToolsPrimaryButton(
                                text = stringResource(R.string.live_status_access_action),
                                onClick = { LiveStatusHub.openNotificationAccessSettings(context) },
                            )
                        }
                        if (capsule && !canOverlay) {
                            OneToolsPrimaryButton(
                                text = stringResource(R.string.live_status_overlay_action),
                                onClick = { LiveStatusHub.openOverlaySettings(context) },
                            )
                        }
                    }
                }
            }
        }
        item {
            OneToolsSection(
                title = stringResource(R.string.live_status_section_sources),
                description = stringResource(R.string.live_status_section_sources_desc),
            ) {
                LiveStatusSource.entries.forEachIndexed { index, source ->
                    if (index > 0) OneToolsGroupDivider()
                    OneToolsSettingsSwitchRow(
                        title = source.labelZh,
                        subtitle = source.packages.firstOrNull().orEmpty(),
                        checked = sourceEnabled[source] == true,
                        enabled = master,
                        onCheckedChange = { enabled ->
                            sourceEnabled = sourceEnabled.toMutableMap().apply {
                                put(source, enabled)
                            }
                            prefs.setSourceEnabled(source, enabled)
                        },
                    )
                }
            }
        }
        item {
            OneToolsSection(
                title = stringResource(R.string.live_status_section_preview),
                description = stringResource(R.string.live_status_section_preview_desc),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = preview?.let {
                            stringResource(R.string.live_status_preview, it)
                        } ?: stringResource(R.string.live_status_preview_idle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.live_status_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_demo),
                        enabled = controlsEnabled,
                        onClick = {
                            if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                            LiveStatusHub.publishDemoMeituan(context, expand = false)
                            preview = "配送中 · 18分钟"
                        },
                    )
                }
                OneToolsGroupDivider()
                OneToolsSettingsActionRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.live_status_more_demos),
                    subtitle = if (showMoreDemos) {
                        stringResource(R.string.live_status_collapse)
                    } else {
                        stringResource(R.string.live_status_more_demos_sub)
                    },
                    enabled = controlsEnabled || showMoreDemos,
                    onClick = { showMoreDemos = !showMoreDemos },
                )
                if (showMoreDemos) {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OneToolsPrimaryButton(
                            text = stringResource(R.string.live_status_demo_meituan_card),
                            enabled = controlsEnabled,
                            onClick = {
                                if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                                LiveStatusHub.publishDemoMeituan(context, expand = true)
                                preview = "美团展开进度卡"
                            },
                        )
                        OneToolsPrimaryButton(
                            text = stringResource(R.string.live_status_demo_didi_detail),
                            enabled = controlsEnabled,
                            onClick = {
                                if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                                LiveStatusHub.publishDemoDidi(context, expand = true)
                                preview = "滴滴关键详情"
                            },
                        )
                        OneToolsPrimaryButton(
                            text = stringResource(R.string.live_status_demo_multi),
                            enabled = controlsEnabled,
                            onClick = {
                                if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                                LiveStatusHub.publishDemoMulti(context)
                                preview = "多任务 · 左右切换"
                            },
                        )
                    }
                }
            }
        }
        item {
            OneToolsSection(
                title = stringResource(R.string.live_status_section_advanced),
                description = stringResource(R.string.live_status_section_advanced_desc),
            ) {
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_status_dynamic_color),
                    subtitle = stringResource(R.string.live_status_dynamic_color_sub),
                    checked = dynamicColor,
                    enabled = controlsEnabled,
                    onCheckedChange = {
                        dynamicColor = it
                        prefs.dynamicColorEnabled = it
                        applyCapsuleLayout()
                    },
                )
                OneToolsGroupDivider()
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_status_haptic),
                    subtitle = stringResource(R.string.live_status_haptic_sub),
                    checked = haptic,
                    enabled = controlsEnabled,
                    onCheckedChange = {
                        haptic = it
                        prefs.hapticEnabled = it
                    },
                )
                OneToolsGroupDivider()
                AdvancedSectionToggle(
                    title = stringResource(R.string.live_status_section_display),
                    subtitle = if (showLayout) {
                        stringResource(R.string.live_status_collapse)
                    } else {
                        stringResource(R.string.live_status_display_summary)
                    },
                    expanded = showLayout,
                    enabled = controlsEnabled || showLayout,
                    onToggle = { showLayout = !showLayout },
                )
                if (showLayout) {
                    CapsuleLayoutControls(
                        exclusionCenter = exclusionCenter,
                        widthScale = widthScale,
                        heightScale = heightScale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        enabled = controlsEnabled,
                        onExclusionChange = {
                            exclusionCenter = it
                            prefs.cameraExclusionMode = if (it) "CAMERA_CENTER" else "BELOW"
                            applyCapsuleLayout()
                        },
                        onWidthChange = {
                            widthScale = it
                            prefs.capsuleWidthScale = it
                            applyCapsuleLayout()
                        },
                        onHeightChange = {
                            heightScale = it
                            prefs.capsuleHeightScale = it
                            applyCapsuleLayout()
                        },
                        onOffsetXChange = {
                            offsetX = it
                            prefs.capsuleOffsetXDp = it
                            applyCapsuleLayout()
                        },
                        onOffsetYChange = {
                            offsetY = it
                            prefs.capsuleOffsetYDp = it
                            applyCapsuleLayout()
                        },
                    )
                }
                OneToolsGroupDivider()
                AdvancedSectionToggle(
                    title = stringResource(R.string.live_status_section_gestures),
                    subtitle = if (showGestures) {
                        stringResource(R.string.live_status_collapse)
                    } else {
                        stringResource(R.string.live_status_gesture_summary)
                    },
                    expanded = showGestures,
                    enabled = controlsEnabled || showGestures,
                    onToggle = { showGestures = !showGestures },
                )
                if (showGestures) {
                    @Suppress("UNUSED_EXPRESSION")
                    gestureEpoch
                    Text(
                        text = stringResource(R.string.live_status_gesture_hint),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CapsuleGestureSlot.entries.forEachIndexed { index, slot ->
                        if (index > 0) OneToolsGroupDivider()
                        val action = prefs.gestureAction(slot)
                        OneToolsSettingsActionRow(
                            icon = Icons.Filled.Star,
                            title = slot.labelZh,
                            subtitle = action.labelZh,
                            enabled = controlsEnabled,
                            onClick = {
                                prefs.setGestureAction(slot, CapsuleGestureDefaults.cycle(action))
                                gestureEpoch += 1
                                applyCapsuleLayout()
                            },
                        )
                    }
                    Column(modifier = Modifier.padding(20.dp)) {
                        OneToolsPrimaryButton(
                            text = stringResource(R.string.live_status_gesture_reset),
                            enabled = controlsEnabled,
                            onClick = {
                                prefs.resetGesturesToDefaults()
                                gestureEpoch += 1
                                applyCapsuleLayout()
                            },
                        )
                    }
                }
                OneToolsGroupDivider()
                AdvancedSectionToggle(
                    title = stringResource(R.string.live_status_section_cutout),
                    subtitle = if (showCutout) {
                        stringResource(R.string.live_status_collapse)
                    } else {
                        stringResource(R.string.live_status_cutout_summary)
                    },
                    expanded = showCutout,
                    enabled = controlsEnabled || showCutout,
                    onToggle = { showCutout = !showCutout },
                )
                if (showCutout) {
                    CutoutCalibrationControls(
                        centerX = cutoutRaw.centerX,
                        centerY = cutoutRaw.centerY,
                        width = cutoutRaw.width,
                        height = cutoutRaw.height,
                        cutoutX = cutoutX,
                        cutoutY = cutoutY,
                        cutoutGap = cutoutGap,
                        enabled = controlsEnabled,
                        onCutoutXChange = {
                            cutoutX = it
                            prefs.cutoutCalibXDp = it
                            applyCapsuleLayout()
                        },
                        onCutoutYChange = {
                            cutoutY = it
                            prefs.cutoutCalibYDp = it
                            applyCapsuleLayout()
                        },
                        onCutoutGapChange = {
                            cutoutGap = it
                            prefs.cutoutGapPadDp = it
                            applyCapsuleLayout()
                        },
                        onAutoDetect = {
                            val detected = CameraAnchorResolver.detectAndPersist(context)
                            cutoutX = 0
                            cutoutY = 0
                            applyCapsuleLayout()
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.live_status_cutout_auto_done,
                                    detected.centerX,
                                    detected.centerY,
                                    detected.width,
                                    detected.height,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        onReset = {
                            prefs.resetCutoutCalibration()
                            cutoutX = 0
                            cutoutY = 0
                            applyCapsuleLayout()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSectionToggle(
    title: String,
    subtitle: String,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    OneToolsSettingsActionRow(
        icon = if (expanded) Icons.Filled.Star else Icons.Filled.Settings,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = onToggle,
    )
}

@Composable
private fun CapsuleLayoutControls(
    exclusionCenter: Boolean,
    widthScale: Float,
    heightScale: Float,
    offsetX: Int,
    offsetY: Int,
    enabled: Boolean,
    onExclusionChange: (Boolean) -> Unit,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onOffsetXChange: (Int) -> Unit,
    onOffsetYChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OneToolsSettingsSwitchRow(
            title = stringResource(R.string.live_status_exclusion),
            subtitle = if (exclusionCenter) {
                stringResource(R.string.live_status_exclusion_center)
            } else {
                stringResource(R.string.live_status_exclusion_below)
            },
            checked = exclusionCenter,
            enabled = enabled,
            onCheckedChange = onExclusionChange,
        )
        LabSlider(
            label = stringResource(R.string.live_status_adjust_width, widthScale * 100f),
            value = widthScale,
            valueRange = 0.7f..1.8f,
            steps = 10,
            enabled = enabled,
            onValueChange = onWidthChange,
        )
        LabSlider(
            label = stringResource(R.string.live_status_adjust_height, heightScale * 100f),
            value = heightScale,
            valueRange = 0.5f..2.2f,
            steps = 16,
            enabled = enabled,
            onValueChange = onHeightChange,
        )
        LabSlider(
            label = stringResource(R.string.live_status_adjust_x, offsetX),
            value = offsetX.toFloat(),
            valueRange = -120f..120f,
            enabled = enabled,
            onValueChange = { onOffsetXChange(it.roundToInt()) },
        )
        LabSlider(
            label = stringResource(R.string.live_status_adjust_y, offsetY),
            value = offsetY.toFloat(),
            valueRange = -40f..120f,
            enabled = enabled,
            onValueChange = { onOffsetYChange(it.roundToInt()) },
        )
    }
}

@Composable
private fun CutoutCalibrationControls(
    centerX: Int,
    centerY: Int,
    width: Int,
    height: Int,
    cutoutX: Int,
    cutoutY: Int,
    cutoutGap: Int,
    enabled: Boolean,
    onCutoutXChange: (Int) -> Unit,
    onCutoutYChange: (Int) -> Unit,
    onCutoutGapChange: (Int) -> Unit,
    onAutoDetect: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.live_status_cutout_info, centerX, centerY, width, height),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.live_status_cutout_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabSlider(
            label = stringResource(R.string.live_status_cutout_x, cutoutX),
            value = cutoutX.toFloat(),
            valueRange = -24f..24f,
            enabled = enabled,
            onValueChange = { onCutoutXChange(it.roundToInt()) },
        )
        LabSlider(
            label = stringResource(R.string.live_status_cutout_y, cutoutY),
            value = cutoutY.toFloat(),
            valueRange = -16f..16f,
            enabled = enabled,
            onValueChange = { onCutoutYChange(it.roundToInt()) },
        )
        LabSlider(
            label = stringResource(
                R.string.live_status_cutout_gap,
                cutoutGap,
                width + cutoutGap,
            ),
            value = cutoutGap.toFloat(),
            valueRange = -12f..48f,
            enabled = enabled,
            onValueChange = { onCutoutGapChange(it.roundToInt()) },
        )
        OneToolsPrimaryButton(
            text = stringResource(R.string.live_status_cutout_auto),
            enabled = enabled,
            onClick = onAutoDetect,
        )
        OneToolsPrimaryButton(
            text = stringResource(R.string.live_status_cutout_reset),
            enabled = enabled,
            onClick = onReset,
        )
    }
}

@Composable
private fun LabSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}