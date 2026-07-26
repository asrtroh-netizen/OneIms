package com.onetools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
    val cutoutRaw = remember(cutoutX, cutoutY, gestureEpoch) {
        CameraAnchorResolver.resolveRaw(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                text = stringResource(R.string.lab_scope_notice),
                danger = false,
            )
        }
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
            OneToolsSection(title = stringResource(R.string.live_status_section_power)) {
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_status_master),
                    subtitle = stringResource(R.string.live_status_master_sub),
                    checked = master,
                    onCheckedChange = {
                        master = it
                        prefs.masterEnabled = it
                        if (!it) {
                            LiveStatusHub.clear(context)
                        } else {
                            LiveStatusHub.refreshCapsuleVisibility(context)
                        }
                        if (it && !access) {
                            Toast.makeText(
                                context,
                                R.string.live_status_access_need,
                                Toast.LENGTH_LONG,
                            ).show()
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
                        LiveStatusHub.refreshCapsuleVisibility(context)
                        if (it && !canOverlay) {
                            Toast.makeText(
                                context,
                                R.string.live_status_overlay_need,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
                if (!access || (capsule && !canOverlay)) {
                    OneToolsGroupDivider()
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            OneToolsSection(title = stringResource(R.string.live_status_section_display)) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OneToolsSettingsSwitchRow(
                        title = stringResource(R.string.live_status_exclusion),
                        subtitle = if (exclusionCenter) {
                            stringResource(R.string.live_status_exclusion_center)
                        } else {
                            stringResource(R.string.live_status_exclusion_below)
                        },
                        checked = exclusionCenter,
                        enabled = master && capsule,
                        onCheckedChange = {
                            exclusionCenter = it
                            prefs.cameraExclusionMode = if (it) "CAMERA_CENTER" else "BELOW"
                            applyCapsuleLayout()
                        },
                    )
                    Text(
                        text = stringResource(
                            R.string.live_status_adjust_width,
                            (widthScale * 100f),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = widthScale,
                        onValueChange = {
                            widthScale = it
                            prefs.capsuleWidthScale = it
                            applyCapsuleLayout()
                        },
                        valueRange = 0.7f..1.8f,
                        steps = 10,
                        enabled = master && capsule,
                    )
                    Text(
                        text = stringResource(
                            R.string.live_status_adjust_height,
                            (heightScale * 100f),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = heightScale,
                        onValueChange = {
                            heightScale = it
                            prefs.capsuleHeightScale = it
                            applyCapsuleLayout()
                        },
                        valueRange = 0.5f..2.2f,
                        steps = 16,
                        enabled = master && capsule,
                    )
                    Text(
                        text = stringResource(R.string.live_status_adjust_x, offsetX),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = offsetX.toFloat(),
                        onValueChange = {
                            val v = it.roundToInt()
                            offsetX = v
                            prefs.capsuleOffsetXDp = v
                            applyCapsuleLayout()
                        },
                        valueRange = -120f..120f,
                        enabled = master && capsule,
                    )
                    Text(
                        text = stringResource(R.string.live_status_adjust_y, offsetY),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = offsetY.toFloat(),
                        onValueChange = {
                            val v = it.roundToInt()
                            offsetY = v
                            prefs.capsuleOffsetYDp = v
                            applyCapsuleLayout()
                        },
                        valueRange = -40f..120f,
                        enabled = master && capsule,
                    )
                }
            }
        }
        item {
            OneToolsSection(title = stringResource(R.string.live_status_section_feel)) {
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_status_dynamic_color),
                    subtitle = stringResource(R.string.live_status_dynamic_color_sub),
                    checked = dynamicColor,
                    enabled = master && capsule,
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
                    enabled = master && capsule,
                    onCheckedChange = {
                        haptic = it
                        prefs.hapticEnabled = it
                    },
                )
            }
        }
        item {
            OneToolsSection(title = stringResource(R.string.live_status_section_gestures)) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.live_status_gesture_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                @Suppress("UNUSED_EXPRESSION")
                gestureEpoch
                CapsuleGestureSlot.entries.forEachIndexed { index, slot ->
                    if (index > 0) OneToolsGroupDivider()
                    val action = prefs.gestureAction(slot)
                    OneToolsSettingsActionRow(
                        icon = Icons.Filled.Star,
                        title = slot.labelZh,
                        subtitle = action.labelZh,
                        enabled = master && capsule,
                        onClick = {
                            val next = CapsuleGestureDefaults.cycle(action)
                            prefs.setGestureAction(slot, next)
                            gestureEpoch += 1
                            applyCapsuleLayout()
                        },
                    )
                }
                OneToolsGroupDivider()
                Column(modifier = Modifier.padding(20.dp)) {
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_gesture_reset),
                        enabled = master && capsule,
                        onClick = {
                            prefs.resetGesturesToDefaults()
                            gestureEpoch += 1
                            applyCapsuleLayout()
                        },
                    )
                }
            }
        }
        item {
            OneToolsSection(title = stringResource(R.string.live_status_section_cutout)) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.live_status_cutout_info,
                            cutoutRaw.centerX,
                            cutoutRaw.centerY,
                            cutoutRaw.width,
                            cutoutRaw.height,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.live_status_cutout_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.live_status_cutout_x, cutoutX),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = cutoutX.toFloat(),
                        onValueChange = {
                            val v = it.roundToInt()
                            cutoutX = v
                            prefs.cutoutCalibXDp = v
                            applyCapsuleLayout()
                        },
                        valueRange = -24f..24f,
                        enabled = master && capsule,
                    )
                    Text(
                        text = stringResource(R.string.live_status_cutout_y, cutoutY),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = cutoutY.toFloat(),
                        onValueChange = {
                            val v = it.roundToInt()
                            cutoutY = v
                            prefs.cutoutCalibYDp = v
                            applyCapsuleLayout()
                        },
                        valueRange = -16f..16f,
                        enabled = master && capsule,
                    )
                    Text(
                        text = stringResource(
                            R.string.live_status_cutout_gap,
                            cutoutGap,
                            cutoutRaw.width + cutoutGap,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = cutoutGap.toFloat(),
                        onValueChange = {
                            val v = it.roundToInt()
                            cutoutGap = v
                            prefs.cutoutGapPadDp = v
                            applyCapsuleLayout()
                        },
                        valueRange = -12f..48f,
                        enabled = master && capsule,
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_cutout_auto),
                        enabled = master && capsule,
                        onClick = {
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
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_cutout_reset),
                        enabled = master && capsule,
                        onClick = {
                            prefs.resetCutoutCalibration()
                            cutoutX = 0
                            cutoutY = 0
                            applyCapsuleLayout()
                        },
                    )
                }
            }
        }
        item {
            OneToolsSection(title = stringResource(R.string.live_status_section_sources)) {
                LiveStatusSource.entries.forEachIndexed { index, source ->
                    if (index > 0) OneToolsGroupDivider()
                    OneToolsSettingsSwitchRow(
                        title = source.labelZh,
                        subtitle = source.packages.firstOrNull().orEmpty(),
                        checked = sourceEnabled[source] == true,
                        enabled = master,
                        onCheckedChange = { on ->
                            sourceEnabled = sourceEnabled.toMutableMap().apply { put(source, on) }
                            prefs.setSourceEnabled(source, on)
                        },
                    )
                }
            }
        }
        item {
            OneToolsSection(title = stringResource(R.string.live_status_section_preview)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = preview?.let {
                            stringResource(R.string.live_status_preview, it)
                        } ?: stringResource(R.string.live_status_preview_idle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.live_status_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_demo),
                        enabled = master && capsule,
                        onClick = {
                            if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                            LiveStatusHub.publishDemoMeituan(context, expand = false)
                            preview = "配送中 · 18分钟"
                        },
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_demo_meituan_card),
                        enabled = master && capsule,
                        onClick = {
                            if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                            LiveStatusHub.publishDemoMeituan(context, expand = true)
                            preview = "美团展开进度卡"
                        },
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_demo_didi_detail),
                        enabled = master && capsule,
                        onClick = {
                            if (!ensureOverlayReady()) return@OneToolsPrimaryButton
                            LiveStatusHub.publishDemoDidi(context, expand = true)
                            preview = "滴滴关键详情"
                        },
                    )
                    OneToolsPrimaryButton(
                        text = stringResource(R.string.live_status_demo_multi),
                        enabled = master && capsule,
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
}
