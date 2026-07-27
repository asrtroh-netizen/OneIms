package com.onetools.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onetools.app.R
import com.onetools.app.channel.ShizukuChannel
import com.onetools.app.recorder.CallRecorderController
import com.onetools.app.recorder.RecorderConsent
import com.onetools.app.recorder.RecordingPlayer
import com.onetools.app.recorder.RecordingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Embedded call-recorder section for the 通话 tab (no separate route). */
fun LazyListScope.recorderPanelItems() {
    item {
        OneToolsSection(
            title = stringResource(R.string.page_oneaudio),
            description = stringResource(R.string.oneaudio_subtitle),
        ) {
            RecorderPanelContent()
        }
    }
}

@androidx.compose.runtime.Composable
private fun RecorderPanelContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { CallRecorderController(context.applicationContext) }
    var consented by remember { mutableStateOf(RecorderConsent.isAccepted(context)) }
    var auto by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(controller.lastStatus) }
    var files by remember { mutableStateOf(RecordingStore.list(context)) }
    var checkLegal by remember { mutableStateOf(consented) }
    var probe by remember { mutableStateOf<String?>(null) }
    var pendingAuto by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (!pendingAuto) {
            status = "权限结果已返回，可再试启动"
            return@rememberLauncherForActivityResult
        }
        pendingAuto = false
        if (controller.hasPhonePermission() && controller.hasMicPermission() && controller.canDrawOverlay()) {
            auto = controller.startMonitoring()
            controller.promptOnCallEnabled = auto
        } else {
            status = "仍缺少录音或通话权限，自动提示未开启"
        }
        files = RecordingStore.list(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && pendingAuto) {
                pendingAuto = false
                if (controller.hasPhonePermission() && controller.hasMicPermission() && controller.canDrawOverlay()) {
                    auto = controller.startMonitoring()
                    controller.promptOnCallEnabled = auto
                }
                files = RecordingStore.list(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            RecordingPlayer.stop()
            controller.dispose()
        }
    }

    fun refresh() {
        status = controller.lastStatus
        files = RecordingStore.list(context)
    }

    fun ensurePerms(): Boolean {
        val need = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
        }
        if (need.isNotEmpty()) {
            permissionLauncher.launch(need.toTypedArray())
            return false
        }
        return true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.recorder_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.recorder_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (ShizukuChannel.isServiceReady()) stringResource(R.string.recorder_shizuku_ok)
            else stringResource(R.string.recorder_shizuku_need),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checkLegal,
                onCheckedChange = {
                    checkLegal = it
                    RecorderConsent.setAccepted(context, it)
                    consented = it
                },
            )
            Text(
                stringResource(R.string.recorder_legal),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = auto,
                onCheckedChange = {
                    if (!it) {
                        pendingAuto = false
                        auto = false
                        controller.promptOnCallEnabled = false
                        controller.stopMonitoring()
                        refresh()
                        return@Checkbox
                    }
                    if (!controller.canDrawOverlay()) {
                        Toast.makeText(context, R.string.recorder_need_overlay, Toast.LENGTH_LONG).show()
                        controller.openOverlaySettings()
                        pendingAuto = true
                        return@Checkbox
                    }
                    if (!ensurePerms()) {
                        pendingAuto = true
                        return@Checkbox
                    }
                    auto = controller.startMonitoring()
                    controller.promptOnCallEnabled = auto
                    refresh()
                },
                enabled = consented,
            )
            Text(
                stringResource(R.string.recorder_prompt_on_call),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Button(
            onClick = {
                if (!consented) {
                    Toast.makeText(context, R.string.recorder_need_legal, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!ensurePerms()) return@Button
                scope.launch {
                    val r = withContext(Dispatchers.IO) { controller.startManual() }
                    r.onSuccess {
                        Toast.makeText(context, R.string.recorder_started, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "fail", Toast.LENGTH_LONG).show()
                    }
                    refresh()
                }
            },
            enabled = consented,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.recorder_start)) }
        OutlinedButton(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { controller.stopManual() }
                    refresh()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.recorder_stop)) }
        OutlinedButton(
            onClick = {
                if (!ShizukuChannel.isServiceReady()) {
                    Toast.makeText(context, R.string.recorder_shizuku_need, Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                scope.launch {
                    val r = withContext(Dispatchers.IO) { controller.probeOemMatrix() }
                    probe = r.getOrElse { "probe fail: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.recorder_probe)) }
        Text(status, style = MaterialTheme.typography.bodySmall)
        probe?.let { p ->
            Text(
                stringResource(R.string.recorder_probe_result, p),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            stringResource(R.string.recorder_list_title),
            style = MaterialTheme.typography.titleSmall,
        )
        files.forEach { f ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${f.name} · ${f.length() / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            RecordingPlayer.play(context, f).onFailure {
                                Toast.makeText(context, it.message ?: "play fail", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text(stringResource(R.string.recorder_play)) }
                    TextButton(
                        onClick = {
                            runCatching { RecordingPlayer.share(context, f) }
                                .onFailure {
                                    Toast.makeText(context, it.message ?: "share fail", Toast.LENGTH_SHORT).show()
                                }
                        },
                    ) { Text(stringResource(R.string.recorder_share)) }
                    TextButton(
                        onClick = {
                            RecordingPlayer.stop()
                            f.delete()
                            refresh()
                        },
                    ) { Text(stringResource(R.string.recorder_delete)) }
                }
            }
        }
    }
}
