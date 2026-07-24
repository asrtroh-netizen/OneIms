package com.onetools.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onetools.app.R
import com.onetools.app.updates.ApkInstaller
import com.onetools.app.updates.TrackedApp
import com.onetools.app.updates.UpdateFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build

private const val TELO_PKG = "vip.mystery0.pixel.telo"
private const val TELO_PKG_DEBUG = "vip.mystery0.pixel.telo.debug"

@Composable
fun TeloScreen(onBack: () -> Unit, onOpenUpdates: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = ApkInstaller.isInstalled(context, TELO_PKG) ||
        ApkInstaller.isInstalled(context, TELO_PKG_DEBUG)
    val pkg = when {
        ApkInstaller.isInstalled(context, TELO_PKG) -> TELO_PKG
        ApkInstaller.isInstalled(context, TELO_PKG_DEBUG) -> TELO_PKG_DEBUG
        else -> TELO_PKG
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← ${stringResource(R.string.telo_title)}") }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.telo_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    if (installed) stringResource(R.string.telo_installed)
                    else stringResource(R.string.telo_not_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (installed) {
                item {
                    Button(
                        onClick = {
                            if (!ApkInstaller.openApp(context, pkg)) {
                                Toast.makeText(context, R.string.telo_open_fail, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.telo_open)) }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            // Guide user to default Caller ID / spam apps settings.
                            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(context, R.string.telo_settings_fail, Toast.LENGTH_LONG).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.telo_set_default)) }
                }
            } else {
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                val app = TrackedApp(
                                    id = "gh-pixeltelo",
                                    title = "Pixel Telo",
                                    packageName = TELO_PKG,
                                    githubOwner = "Pixel-Tailor-CN",
                                    githubRepo = "PixelTelo",
                                    assetPrefer = listOf("PixelTelo", ".apk"),
                                    note = "Apache-2.0",
                                )
                                val abis = Build.SUPPORTED_ABIS.toList()
                                runCatching {
                                    if (!ApkInstaller.canRequestPackageInstalls(context)) {
                                        ApkInstaller.openUnknownSourcesSettings(context)
                                        error(context.getString(R.string.updates_need_install_perm))
                                    }
                                    val asset = withContext(Dispatchers.IO) {
                                        UpdateFetcher.latestAsset(app, abis).getOrThrow()
                                    }
                                    val file = ApkInstaller.cacheApkFile(
                                        context,
                                        "pixeltelo-${asset.tag}-${asset.name}",
                                    )
                                    withContext(Dispatchers.IO) {
                                        UpdateFetcher.downloadToFile(asset.downloadUrl, file)
                                    }
                                    ApkInstaller.installApk(context, file)
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "install fail",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.telo_install)) }
                }
                item {
                    OutlinedButton(
                        onClick = onOpenUpdates,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.telo_via_updates)) }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("https://github.com/Pixel-Tailor-CN/PixelTelo")
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.telo_github)) }
            }
            item {
                Text(
                    stringResource(R.string.telo_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
