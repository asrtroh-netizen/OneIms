package com.onetools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import kotlin.math.roundToInt

@Composable
fun LiveLabScreen() {
    val context = LocalContext.current
    val prefs = remember { LiveStatusPrefs(context) }
    var master by remember { mutableStateOf(prefs.masterEnabled) }
    var capsule by remember { mutableStateOf(prefs.capsuleEnabled) }
    var access by remember { mutableStateOf(LiveStatusHub.isNotificationAccessEnabled(context)) }
    var canOverlay by remember { mutableStateOf(LiveStatusCapsuleOverlay.get(context).canDraw()) }
    var meituan by remember { mutableStateOf(prefs.isSourceEnabled(LiveStatusSource.MEITUAN)) }
    var didi by remember { mutableStateOf(prefs.isSourceEnabled(LiveStatusSource.DIDI)) }
    var cainiao by remember { mutableStateOf(prefs.isSourceEnabled(LiveStatusSource.CAINIAO)) }
    var preview by remember { mutableStateOf(LiveStatusHub.lastChipText()) }
    var widthScale by remember { mutableFloatStateOf(prefs.capsuleWidthScale) }
    var heightScale by remember { mutableFloatStateOf(prefs.capsuleHeightScale) }
    var offsetX by remember { mutableIntStateOf(prefs.capsuleOffsetXDp) }
    var offsetY by remember { mutableIntStateOf(prefs.capsuleOffsetYDp) }
    var exclusionCenter by remember {
        mutableStateOf(prefs.cameraExclusionMode == "CAMERA_CENTER")
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
            OneToolsSection(title = stringResource(R.string.live_status_master)) {
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
            OneToolsSection(title = stringResource(R.string.live_status_adjust)) {
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
                        valueRange = 0.6f..1.5f,
                        steps = 8,
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
            OneToolsSection(title = stringResource(R.string.live_status_sources)) {
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_source_meituan),
                    subtitle = "com.sankuai.meituan*",
                    checked = meituan,
                    enabled = master,
                    onCheckedChange = {
                        meituan = it
                        prefs.setSourceEnabled(LiveStatusSource.MEITUAN, it)
                    },
                )
                OneToolsGroupDivider()
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_source_didi),
                    subtitle = "com.sdu.didi.psnger",
                    checked = didi,
                    enabled = master,
                    onCheckedChange = {
                        didi = it
                        prefs.setSourceEnabled(LiveStatusSource.DIDI, it)
                    },
                )
                OneToolsGroupDivider()
                OneToolsSettingsSwitchRow(
                    title = stringResource(R.string.live_source_cainiao),
                    subtitle = "com.cainiao.wireless",
                    checked = cainiao,
                    enabled = master,
                    onCheckedChange = {
                        cainiao = it
                        prefs.setSourceEnabled(LiveStatusSource.CAINIAO, it)
                    },
                )
            }
        }
        item {
            OneToolsSection(title = stringResource(R.string.live_status_preview_section)) {
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
