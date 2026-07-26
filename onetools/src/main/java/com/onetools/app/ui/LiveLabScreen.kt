package com.onetools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.onetools.app.live.LiveStatusHub
import com.onetools.app.live.LiveStatusPrefs
import com.onetools.app.live.LiveStatusSource

@Composable
fun LiveLabScreen() {
    val context = LocalContext.current
    val prefs = remember { LiveStatusPrefs(context) }
    var master by remember { mutableStateOf(prefs.masterEnabled) }
    var access by remember { mutableStateOf(LiveStatusHub.isNotificationAccessEnabled(context)) }
    var meituan by remember { mutableStateOf(prefs.isSourceEnabled(LiveStatusSource.MEITUAN)) }
    var didi by remember { mutableStateOf(prefs.isSourceEnabled(LiveStatusSource.DIDI)) }
    var cainiao by remember { mutableStateOf(prefs.isSourceEnabled(LiveStatusSource.CAINIAO)) }
    var preview by remember { mutableStateOf(LiveStatusHub.lastChipText()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = LiveStatusHub.isNotificationAccessEnabled(context)
                preview = LiveStatusHub.lastChipText()
                master = prefs.masterEnabled
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
                        if (!it) LiveStatusHub.clear(context)
                        if (it && !access) {
                            Toast.makeText(
                                context,
                                R.string.live_status_access_need,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
                if (!access) {
                    OneToolsGroupDivider()
                    Column(modifier = Modifier.padding(20.dp)) {
                        OneToolsPrimaryButton(
                            text = stringResource(R.string.live_status_access_action),
                            onClick = { LiveStatusHub.openNotificationAccessSettings(context) },
                        )
                    }
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
                }
            }
        }
    }
}
