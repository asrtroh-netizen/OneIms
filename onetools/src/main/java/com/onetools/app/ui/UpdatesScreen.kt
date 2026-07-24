package com.onetools.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.updates.ApkInstaller
import com.onetools.app.updates.GitHubReleaseClient
import com.onetools.app.updates.InstalledVersions
import com.onetools.app.updates.ReleaseAsset
import com.onetools.app.updates.TrackedApp
import com.onetools.app.updates.TrackedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    val latestById = remember { mutableStateMapOf<String, ReleaseAsset>() }

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← ${stringResource(R.string.updates_title)}") }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.updates_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (progress != null) {
                item {
                    val (done, total) = progress!!
                    LinearProgressIndicator(
                        progress = if (total > 0) {
                            (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            banner?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall) }
            }
            items(TrackedApps.all, key = { it.id }) { app ->
                val installedVer = InstalledVersions.versionName(context, app.packageName)
                val latest = latestById[app.id]
                UpdateAppCard(
                    app = app,
                    installed = ApkInstaller.isInstalled(context, app.packageName),
                    installedVersion = installedVer,
                    latest = latest,
                    busy = busyId == app.id,
                    enabled = busyId == null,
                    onCheck = {
                        scope.launch {
                            busyId = app.id
                            banner = context.getString(R.string.updates_checking, app.title)
                            val result = withContext(Dispatchers.IO) {
                                GitHubReleaseClient.latestAsset(app)
                            }
                            result.onSuccess { asset ->
                                latestById[app.id] = asset
                                banner = context.getString(
                                    R.string.updates_found,
                                    app.title,
                                    asset.tag,
                                    asset.name,
                                )
                            }.onFailure {
                                banner = "${app.title}: ${it.message ?: "error"}"
                            }
                            busyId = null
                        }
                    },
                    onInstallOrOpen = {
                        val pkg = app.packageName
                        if (pkg != null && ApkInstaller.isInstalled(context, pkg) && latest == null) {
                            ApkInstaller.openApp(context, pkg)
                            return@UpdateAppCard
                        }
                        if (!ApkInstaller.canRequestPackageInstalls(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.updates_need_install_perm),
                                Toast.LENGTH_LONG,
                            ).show()
                            ApkInstaller.openUnknownSourcesSettings(context)
                            return@UpdateAppCard
                        }
                        scope.launch {
                            busyId = app.id
                            progress = 0L to 0L
                            try {
                                val asset = latestById[app.id]
                                    ?: withContext(Dispatchers.IO) {
                                        GitHubReleaseClient.latestAsset(app).getOrThrow()
                                    }.also { latestById[app.id] = it }
                                val file = ApkInstaller.cacheApkFile(
                                    context,
                                    "${app.id}-${asset.tag}-${asset.name}",
                                )
                                withContext(Dispatchers.IO) {
                                    GitHubReleaseClient.downloadToFile(asset.downloadUrl, file) { d, t ->
                                        progress = d to t
                                    }
                                }
                                banner = context.getString(R.string.updates_downloaded, file.name)
                                ApkInstaller.installApk(context, file)
                            } catch (e: Exception) {
                                banner = e.message ?: "download failed"
                                Toast.makeText(context, banner, Toast.LENGTH_LONG).show()
                            } finally {
                                busyId = null
                                progress = null
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdateAppCard(
    app: TrackedApp,
    installed: Boolean,
    installedVersion: String?,
    latest: ReleaseAsset?,
    busy: Boolean,
    enabled: Boolean,
    onCheck: () -> Unit,
    onInstallOrOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(app.title, style = MaterialTheme.typography.titleMedium)
            Text(
                app.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buildString {
                    append(
                        if (installed) {
                            stringResource(R.string.updates_installed)
                        } else {
                            stringResource(R.string.updates_not_installed)
                        },
                    )
                    if (!installedVersion.isNullOrBlank()) {
                        append(" · v")
                        append(installedVersion)
                    }
                    if (latest != null) {
                        append(" → ")
                        append(latest.tag)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(
                onClick = onCheck,
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.updates_check))
            }
            Button(
                onClick = onInstallOrOpen,
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        latest != null -> stringResource(R.string.updates_download_install)
                        installed -> stringResource(R.string.updates_open)
                        else -> stringResource(R.string.updates_download_install)
                    },
                )
            }
        }
    }
}
