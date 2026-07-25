package com.onetools.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.onetools.app.R
import com.onetools.app.caller.CnMobileGeo
import com.onetools.app.caller.NumberMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight 通话页：归属试查 + OneAudio 录音。无拦截/骚扰库/举报 UI。
 */
@Composable
fun CallLiteScreen(showBack: Boolean = false, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lookup by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var callLogOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val callLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> callLogOk = granted }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showBack) {
            OneToolsToolHeader(title = stringResource(R.string.page_onecaller), onBack = onBack)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                // Dock tabs already get status-bar inset from Scaffold; match OneImsPage top=28.
                top = if (showBack) 8.dp else 28.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.page_onecaller),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.onecaller_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                OneToolsInfoCard(
                    title = stringResource(R.string.onecaller_geo_card),
                    subtitle = stringResource(R.string.onecaller_geo_card_sub),
                )
            }
            item {
                if (!callLogOk) {
                    Button(
                        onClick = { callLogLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.caller_need_call_log)) }
                } else {
                    Text(
                        stringResource(R.string.caller_call_log_ok),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.caller_lookup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                OutlinedTextField(
                    value = lookup,
                    onValueChange = { lookup = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caller_lookup_hint)) },
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            result = withContext(Dispatchers.Default) {
                                val digits = NumberMatcher.digits(lookup)
                                val geo = CnMobileGeo.lookup(context, digits)
                                if (geo == null) {
                                    context.getString(R.string.onecaller_geo_miss)
                                } else {
                                    context.getString(
                                        R.string.onecaller_geo_hit,
                                        formatPhone(digits),
                                        geo.dialerLine(),
                                    )
                                }
                            }
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.caller_lookup)) }
            }
            if (result.isNotBlank()) {
                item {
                    Text(result, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    stringResource(R.string.page_oneaudio),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.oneaudio_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
            recorderPanelItems()
        }
    }
}

private fun formatPhone(digits: String): String {
    val d = digits.removePrefix("86")
    return if (d.length == 11) {
        "${d.substring(0, 3)} ${d.substring(3, 7)} ${d.substring(7)}"
    } else {
        d
    }
}
