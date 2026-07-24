package com.onetools.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.onetools.app.R
import com.onetools.app.channel.ShizukuChannel
import com.onetools.app.recorder.CallRecorderController
import com.onetools.app.recorder.RecorderConsent
import com.onetools.app.recorder.RecordingPlayer
import com.onetools.app.recorder.RecordingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecorderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { CallRecorderController(context.applicationContext) }
    var consented by remember { mutableStateOf(RecorderConsent.isAccepted(context)) }
    var auto by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(controller.lastStatus) }
    var files by remember { mutableStateOf(RecordingStore.list(context)) }
    var checkLegal by remember { mutableStateOf(consented) }
    var probe by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        status = "权限结果已返回，可再试启动"
    }

    DisposableEffect(Unit) {
        onDispose {
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

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← ${stringResource(R.string.recorder_title)}") }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.recorder_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    if (ShizukuChannel.isServiceReady()) stringResource(R.string.recorder_shizuku_ok)
                    else stringResource(R.string.recorder_shizuku_need),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
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
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = auto,
                        onCheckedChange = {
                            auto = it
                            controller.autoEnabled = it
                            if (it) {
                                if (!ensurePerms()) return@Checkbox
                                controller.startMonitoring()
                            } else {
                                controller.stopMonitoring()
                            }
                            refresh()
                        },
                        enabled = consented,
                    )
                    Text(
                        stringResource(R.string.recorder_auto),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            item {
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
            }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { controller.stopManual() }
                            refresh()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.recorder_stop)) }
            }
            item {
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
            }
            item {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            probe?.let { p ->
                item {
                    Text(
                        stringResource(R.string.recorder_probe_result, p),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.recorder_list_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(files, key = { it.absolutePath }) { f ->
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
}
