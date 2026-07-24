package com.onetools.app.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.onetools.app.BuildConfig
import com.onetools.app.R
import com.onetools.app.caller.BlocklistFormat
import com.onetools.app.caller.CallMatchMode
import com.onetools.app.caller.CallRule
import com.onetools.app.caller.CallRuleKind
import com.onetools.app.caller.CallRuleStore
import com.onetools.app.caller.NumberMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Composable
fun CallerScreen(
    onBack: () -> Unit,
    onOpenRecorder: () -> Unit,
    onOpenTelo: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CallRuleStore(context.applicationContext) }
    val rules by store.rules.collectAsState(initial = emptyList())

    var number by remember { mutableStateOf("") }
    var asPrefix by remember { mutableStateOf(false) }
    var asTag by remember { mutableStateOf(false) }
    var asAllow by remember { mutableStateOf(false) }
    var ruleTag by remember { mutableStateOf("") }
    var lookupNumber by remember { mutableStateOf("") }
    var lookupResult by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        status = if (result.resultCode == Activity.RESULT_OK) {
            context.getString(R.string.caller_role_granted)
        } else {
            context.getString(R.string.caller_role_denied)
        }
    }

    val callLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        status = if (granted) {
            "通话记录权限已授予 · Directory 可参与查号"
        } else {
            context.getString(R.string.caller_need_call_log)
        }
    }

    fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status = context.getString(R.string.caller_role_need_q)
            return
        }
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            status = context.getString(R.string.caller_role_unavailable)
            return
        }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            status = context.getString(R.string.caller_role_held)
            return
        }
        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    fun openDefaultApps() {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        Toast.makeText(context, R.string.caller_settings_fail, Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← ${stringResource(R.string.caller_title)}") }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.caller_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    stringResource(R.string.caller_gap_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(onClick = { requestScreeningRole() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.caller_set_default))
                }
            }
            item {
                OutlinedButton(onClick = { openDefaultApps() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.caller_open_defaults))
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CALL_LOG,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            status = "通话记录权限已具备"
                        } else {
                            callLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.caller_need_call_log))
                }
            }
            if (status.isNotBlank()) {
                item {
                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Text(stringResource(R.string.caller_add_title), style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caller_number_hint)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = ruleTag,
                    onValueChange = { ruleTag = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caller_tag_hint)) },
                    singleLine = true,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = asPrefix,
                        onCheckedChange = {
                            asPrefix = it
                            if (it) asTag = false
                        },
                    )
                    Text(stringResource(R.string.caller_prefix))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = asTag,
                        onCheckedChange = {
                            asTag = it
                            if (it) asPrefix = false
                        },
                    )
                    Text(stringResource(R.string.caller_tag_mode))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = asAllow, onCheckedChange = { asAllow = it })
                    Text(stringResource(R.string.caller_allow))
                }
            }
            item {
                Button(
                    onClick = {
                        val pattern = if (asTag) {
                            ruleTag.ifBlank { number }.trim()
                        } else {
                            NumberMatcher.digits(number)
                        }
                        if (pattern.isEmpty()) {
                            Toast.makeText(context, R.string.caller_number_invalid, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            store.upsert(
                                CallRule(
                                    id = UUID.randomUUID().toString(),
                                    pattern = pattern,
                                    kind = if (asAllow) CallRuleKind.ALLOW else CallRuleKind.BLOCK,
                                    mode = when {
                                        asTag -> CallMatchMode.TAG
                                        asPrefix -> CallMatchMode.PREFIX
                                        else -> CallMatchMode.EXACT
                                    },
                                    tag = ruleTag,
                                ),
                            )
                            number = ""
                            status = context.getString(R.string.caller_added)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.caller_add))
                }
            }
            item {
                Text(stringResource(R.string.caller_lookup_title), style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = lookupNumber,
                    onValueChange = { lookupNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caller_lookup_hint)) },
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val r = store.lookup(lookupNumber)
                            lookupResult = buildString {
                                append(r.decision.name)
                                if (r.tags.isNotEmpty()) append(" · tags=").append(r.tags.joinToString(","))
                                if (r.matchedRules.isNotEmpty()) {
                                    append(" · hits=").append(r.matchedRules.size)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.caller_lookup)) }
            }
            if (lookupResult.isNotBlank()) {
                item {
                    Text(lookupResult, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Text(stringResource(R.string.caller_import_title), style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.caller_import_hint)) },
                    minLines = 3,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { importText = BlocklistFormat.sampleJson() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.caller_sample)) }
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val parsed = BlocklistFormat.parse(importText)
                                    store.mergeImport(parsed)
                                    status = context.getString(R.string.caller_imported, parsed.size)
                                }.onFailure {
                                    status = it.message ?: "import failed"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.caller_import)) }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            status = context.getString(R.string.caller_fetching)
                            val result = withContext(Dispatchers.IO) {
                                runCatching { fetchBlocklist() }
                            }
                            result.onSuccess { body ->
                                runCatching {
                                    val parsed = BlocklistFormat.parse(body)
                                    store.mergeImport(parsed)
                                    importText = body.take(2000)
                                    status = context.getString(R.string.caller_imported, parsed.size)
                                }.onFailure { status = it.message ?: "parse failed" }
                            }.onFailure {
                                status = context.getString(R.string.caller_fetch_fail, it.message ?: "")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.caller_fetch_cdn))
                }
            }
            item {
                Text(
                    stringResource(R.string.caller_rules_title, rules.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(rules, key = { it.id }) { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${rule.kind.name} · ${rule.mode.name} · ${rule.pattern}" +
                                if (rule.tag.isNotBlank()) " · ${rule.tag}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = { scope.launch { store.remove(rule.id) } }) {
                        Text(stringResource(R.string.caller_remove))
                    }
                }
            }
            item {
                OutlinedButton(onClick = onOpenRecorder, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.caller_open_recorder))
                }
            }
            item {
                TextButton(onClick = onOpenTelo) {
                    Text(stringResource(R.string.caller_open_telo))
                }
            }
        }
    }
}

private fun blocklistUrls(): List<String> {
    val primary = BuildConfig.ONE_BLOCKLIST_URL
    val releaseMirror =
        "https://github.com/asrtroh-netizen/OneBlock/releases/download/onetools-cdn-assets/one-blocklist.json"
    val cdn = BuildConfig.ONE_CDN_INDEX_URL.let { index ->
        if (index.contains("one-update.json")) index.replace("one-update.json", "one-blocklist.json")
        else "https://cdn.oneims.app/onetools/one-blocklist.json"
    }
    return listOf(primary, releaseMirror, cdn).distinct()
}

private fun fetchBlocklist(url: String): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 20_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP $code ${body.take(120)}")
        return body
    } finally {
        conn.disconnect()
    }
}

private fun fetchBlocklist(): String {
    var last: Throwable? = null
    for (url in blocklistUrls()) {
        try {
            return fetchBlocklist(url)
        } catch (t: Throwable) {
            last = t
        }
    }
    throw last ?: IllegalStateException("no blocklist URL")
}
