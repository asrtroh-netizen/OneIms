package com.onetools.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.updates.ApkInstaller
import com.onetools.app.updates.AppSource
import com.onetools.app.updates.CatalogExport
import com.onetools.app.updates.GitHubRepoParser
import com.onetools.app.updates.InstalledVersions
import com.onetools.app.updates.ReleaseAsset
import com.onetools.app.updates.ShizukuApkInstaller
import com.onetools.app.updates.TrackedApp
import com.onetools.app.updates.UpdateCatalogRepository
import com.onetools.app.updates.UpdateFetcher
import com.onetools.app.updates.VersionCompare
import com.onetools.app.updates.withPackageName
import com.onetools.app.channel.ShizukuChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { UpdateCatalogRepository(context.applicationContext) }
    val apps by repo.apps.collectAsState(initial = emptyList())
    val abis = remember { Build.SUPPORTED_ABIS.toList() }

    var busyId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    val latestById = remember { mutableStateMapOf<String, ReleaseAsset>() }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var notesFor by remember { mutableStateOf<ReleaseAsset?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { repo.ensureSeeded() }

    val sortedApps = remember(apps, latestById.toMap()) {
        apps.sortedWith(
            compareBy<TrackedApp> { app ->
                val installed = InstalledVersions.versionName(context, app.packageName)
                val latest = latestById[app.id]?.tag
                when (VersionCompare.state(installed, latest)) {
                    VersionCompare.UpdateState.UPDATE_AVAILABLE -> 0
                    VersionCompare.UpdateState.NOT_INSTALLED -> 1
                    VersionCompare.UpdateState.UNKNOWN -> 2
                    VersionCompare.UpdateState.UP_TO_DATE -> 3
                }
            }.thenBy { it.title.lowercase() },
        )
    }

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
                    stringResource(R.string.updates_intro_better),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    stringResource(R.string.updates_abi_hint, abis.firstOrNull() ?: "unknown"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = busyId == null && !adding,
                ) {
                    Text(stringResource(R.string.updates_add))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val json = CatalogExport.toJson(apps)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("onetools-catalog", json))
                            Toast.makeText(context, R.string.updates_exported, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = apps.isNotEmpty(),
                    ) { Text(stringResource(R.string.updates_export)) }
                    OutlinedButton(
                        onClick = { showImport = true },
                        modifier = Modifier.weight(1f),
                        enabled = busyId == null,
                    ) { Text(stringResource(R.string.updates_import)) }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            banner = context.getString(R.string.updates_checking_all)
                            var ok = 0
                            var updates = 0
                            for (app in apps) {
                                busyId = app.id
                                val result = withContext(Dispatchers.IO) {
                                    UpdateFetcher.latestAsset(app, abis)
                                }
                                result.onSuccess { asset ->
                                    latestById[app.id] = asset
                                    ok++
                                    val installed = InstalledVersions.versionName(context, app.packageName)
                                    if (VersionCompare.state(installed, asset.tag) ==
                                        VersionCompare.UpdateState.UPDATE_AVAILABLE
                                    ) {
                                        updates++
                                    }
                                }
                            }
                            busyId = null
                            banner = context.getString(R.string.updates_check_all_summary, ok, updates)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = busyId == null && apps.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.updates_check_all))
                }
            }
            if (progress != null) {
                item {
                    val (done, total) = progress!!
                    LinearProgressIndicator(
                        progress = {
                            if (total > 0) {
                                (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            banner?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall) }
            }
            items(sortedApps, key = { it.id }) { app ->
                val installedVer = InstalledVersions.versionName(context, app.packageName)
                val latest = latestById[app.id]
                val state = VersionCompare.state(installedVer, latest?.tag)
                UpdateAppCard(
                    app = app,
                    installed = ApkInstaller.isInstalled(context, app.packageName),
                    installedVersion = installedVer,
                    latest = latest,
                    state = state,
                    busy = busyId == app.id,
                    enabled = busyId == null && !adding,
                    onCheck = {
                        scope.launch {
                            busyId = app.id
                            banner = context.getString(R.string.updates_checking, app.title)
                            val result = withContext(Dispatchers.IO) {
                                UpdateFetcher.latestAsset(app, abis)
                            }
                            result.onSuccess {
                                latestById[app.id] = it
                                val st = VersionCompare.state(
                                    InstalledVersions.versionName(context, app.packageName),
                                    it.tag,
                                )
                                banner = context.getString(
                                    R.string.updates_found_state,
                                    app.title,
                                    it.tag,
                                    it.name,
                                    stateLabel(context, st),
                                )
                            }.onFailure {
                                banner = "${app.title}: ${it.message ?: "error"}"
                            }
                            busyId = null
                        }
                    },
                    onNotes = { latest?.let { notesFor = it } },
                    onInstall = {
                        val canSilent = ShizukuApkInstaller.isAvailable()
                        if (!canSilent && !ApkInstaller.canRequestPackageInstalls(context)) {
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
                                        UpdateFetcher.latestAsset(app, abis).getOrThrow()
                                    }.also { latestById[app.id] = it }
                                val file = ApkInstaller.cacheApkFile(
                                    context,
                                    "${app.id}-${asset.tag}-${asset.name}",
                                )
                                withContext(Dispatchers.IO) {
                                    UpdateFetcher.downloadToFile(asset.downloadUrl, file) { d, t ->
                                        progress = d to t
                                    }
                                }
                                val detected = ApkInstaller.packageNameFromApk(context, file)
                                if (!detected.isNullOrBlank() && detected != app.packageName) {
                                    repo.update(app.withPackageName(detected))
                                    banner = context.getString(
                                        R.string.updates_bound_package,
                                        detected,
                                    )
                                } else {
                                    banner = context.getString(R.string.updates_downloaded, file.name)
                                }
                                val silentOk = ShizukuApkInstaller.isAvailable()
                                if (silentOk) {
                                    val silent = withContext(Dispatchers.IO) {
                                        ShizukuApkInstaller.install(context, file)
                                    }
                                    silent.onSuccess {
                                        banner = context.getString(R.string.updates_silent_ok)
                                        Toast.makeText(context, banner, Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        banner = context.getString(
                                            R.string.updates_silent_fallback,
                                            err.message ?: "error",
                                        )
                                        ApkInstaller.installApk(context, file)
                                    }
                                } else {
                                    ApkInstaller.installApk(context, file)
                                }
                            } catch (e: Exception) {
                                banner = e.message ?: "download failed"
                                Toast.makeText(context, banner, Toast.LENGTH_LONG).show()
                            } finally {
                                busyId = null
                                progress = null
                            }
                        }
                    },
                    onOpen = {
                        val pkg = app.packageName
                        if (pkg != null && ApkInstaller.isInstalled(context, pkg)) {
                            ApkInstaller.openApp(context, pkg)
                        }
                    },
                    onRemove = {
                        scope.launch { repo.remove(app.id) }
                    },
                )
            }
        }
    }

    if (showAdd) {
        AddAppDialog(
            busy = adding,
            shizukuReady = ShizukuChannel.isServiceReady(),
            onDismiss = { if (!adding) showAdd = false },
            onConfirm = { input, title, packageName, source, host ->
                scope.launch {
                    adding = true
                    val parsed = GitHubRepoParser.parse(
                        raw = input,
                        titleOverride = title,
                        sourceHint = source,
                        packageName = packageName,
                        hostOverride = host,
                    )
                    parsed.onFailure {
                        Toast.makeText(context, it.message ?: "invalid", Toast.LENGTH_LONG).show()
                        adding = false
                        return@launch
                    }
                    val base = parsed.getOrThrow()
                    val valid = withContext(Dispatchers.IO) { UpdateFetcher.validate(base) }
                    valid.onFailure {
                        Toast.makeText(context, it.message ?: "validate failed", Toast.LENGTH_LONG).show()
                        adding = false
                        return@launch
                    }
                    repo.add(base)
                    showAdd = false
                    adding = false
                    banner = context.getString(R.string.updates_added, base.title)
                }
            },
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onMerge = { raw ->
                runCatching { CatalogExport.fromJson(raw) }
                    .onSuccess { list ->
                        scope.launch {
                            repo.mergeAll(list)
                            showImport = false
                            banner = context.getString(R.string.updates_imported, list.size)
                        }
                    }
                    .onFailure {
                        Toast.makeText(context, it.message ?: "bad json", Toast.LENGTH_LONG).show()
                    }
            },
        )
    }

    notesFor?.let { asset ->
        AlertDialog(
            onDismissRequest = { notesFor = null },
            title = { Text("${asset.tag} · ${asset.name}") },
            text = {
                Text(
                    asset.body.ifBlank { stringResource(R.string.updates_no_notes) },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { notesFor = null }) {
                    Text(stringResource(R.string.updates_cancel))
                }
            },
        )
    }
}

private fun stateLabel(context: Context, state: VersionCompare.UpdateState): String {
    return when (state) {
        VersionCompare.UpdateState.UPDATE_AVAILABLE ->
            context.getString(R.string.updates_state_available)
        VersionCompare.UpdateState.UP_TO_DATE ->
            context.getString(R.string.updates_state_latest)
        VersionCompare.UpdateState.NOT_INSTALLED ->
            context.getString(R.string.updates_not_installed)
        VersionCompare.UpdateState.UNKNOWN ->
            context.getString(R.string.updates_state_unknown)
    }
}

@Composable
private fun AddAppDialog(
    busy: Boolean,
    shizukuReady: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (input: String, title: String, packageName: String, source: AppSource, host: String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(AppSource.GITHUB) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.updates_add_hint_multi),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (shizukuReady) stringResource(R.string.updates_silent_ready)
                    else stringResource(R.string.updates_silent_need_channel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceChip("GitHub", source == AppSource.GITHUB, enabled = !busy) {
                        source = AppSource.GITHUB
                    }
                    SourceChip("GitLab", source == AppSource.GITLAB, enabled = !busy) {
                        source = AppSource.GITLAB
                    }
                    SourceChip("F-Droid", source == AppSource.FDROID, enabled = !busy) {
                        source = AppSource.FDROID
                    }
                    SourceChip("One", source == AppSource.ONE_INDEX, enabled = !busy) {
                        source = AppSource.ONE_INDEX
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = {
                        Text(
                            when (source) {
                                AppSource.FDROID -> stringResource(R.string.updates_add_fdroid)
                                AppSource.ONE_INDEX -> stringResource(R.string.updates_add_one_index)
                                AppSource.GITLAB -> stringResource(R.string.updates_add_repo)
                                AppSource.GITHUB -> stringResource(R.string.updates_add_repo)
                            },
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (source == AppSource.GITLAB || source == AppSource.FDROID || source == AppSource.ONE_INDEX) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = {
                            Text(
                                when (source) {
                                    AppSource.GITLAB -> stringResource(R.string.updates_add_gitlab_host)
                                    AppSource.FDROID -> stringResource(R.string.updates_add_fdroid_host)
                                    AppSource.ONE_INDEX -> stringResource(R.string.updates_add_one_index_url)
                                    else -> stringResource(R.string.updates_add_gitlab_host)
                                },
                            )
                        },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.updates_add_name)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(stringResource(R.string.updates_add_package)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Text(
                        stringResource(R.string.updates_validating),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input, title, packageName, source, host) },
                enabled = !busy && input.isNotBlank(),
            ) {
                Text(stringResource(R.string.updates_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.updates_cancel))
            }
        },
    )
}

@Composable
private fun SourceChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_import_title)) },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                label = { Text(stringResource(R.string.updates_import_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
        },
        confirmButton = {
            TextButton(onClick = { onMerge(raw) }, enabled = raw.isNotBlank()) {
                Text(stringResource(R.string.updates_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.updates_cancel))
            }
        },
    )
}

@Composable
private fun UpdateAppCard(
    app: TrackedApp,
    installed: Boolean,
    installedVersion: String?,
    latest: ReleaseAsset?,
    state: VersionCompare.UpdateState,
    busy: Boolean,
    enabled: Boolean,
    onCheck: () -> Unit,
    onNotes: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val statusColor = when (state) {
        VersionCompare.UpdateState.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.tertiary
        VersionCompare.UpdateState.UP_TO_DATE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove, enabled = enabled && !busy) {
                    Text(stringResource(R.string.updates_remove))
                }
            }
            Text(
                buildString {
                    append(app.source.name)
                    append(" · ")
                    append(app.note.ifBlank { "github.com/${app.githubOwner}/${app.githubRepo}" })
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!app.packageName.isNullOrBlank()) {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                buildString {
                    append(stateLabel(LocalContext.current, state))
                    if (!installedVersion.isNullOrBlank()) {
                        append(" · v")
                        append(installedVersion)
                    }
                    if (latest != null) {
                        append(" → ")
                        append(latest.tag)
                        append(" · ")
                        append(latest.name)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = onCheck,
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.updates_check)) }
            if (latest != null && latest.body.isNotBlank()) {
                TextButton(
                    onClick = onNotes,
                    enabled = enabled && !busy,
                ) { Text(stringResource(R.string.updates_notes)) }
            }
            Button(
                onClick = onInstall,
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        ShizukuApkInstaller.isAvailable() &&
                            state == VersionCompare.UpdateState.UPDATE_AVAILABLE ->
                            stringResource(R.string.updates_download_silent_update)
                        ShizukuApkInstaller.isAvailable() ->
                            stringResource(R.string.updates_download_silent)
                        state == VersionCompare.UpdateState.UPDATE_AVAILABLE ->
                            stringResource(R.string.updates_download_update)
                        else -> stringResource(R.string.updates_download_install)
                    },
                )
            }
            if (installed) {
                OutlinedButton(
                    onClick = onOpen,
                    enabled = enabled && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.updates_open)) }
            }
        }
    }
}
