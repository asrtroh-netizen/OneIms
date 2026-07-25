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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CallRuleStore(context.applicationContext) }
    val rules by store.rules.collectAsState(initial = emptyList())
    val lifecycleOwner = LocalLifecycleOwner.current

    var number by remember { mutableStateOf("") }
    var asPrefix by remember { mutableStateOf(false) }
    var asTag by remember { mutableStateOf(false) }
    /** 0=归属标签 1=拦截 2=白名单 */
    var ruleAction by remember { mutableIntStateOf(0) }
    var ruleTag by remember { mutableStateOf("") }
    var lookupNumber by remember { mutableStateOf("") }
    var lookupResult by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var roleHeld by remember { mutableStateOf(false) }
    var callLogGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    fun refreshRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            roleHeld = false
            return
        }
        val rm = context.getSystemService(RoleManager::class.java)
        roleHeld = rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshRole()
            callLogGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        refreshRole()
        status = if (result.resultCode == Activity.RESULT_OK) {
            context.getString(R.string.caller_role_granted)
        } else {
            context.getString(R.string.caller_role_denied)
        }
    }

    val callLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        callLogGranted = granted
        status = if (granted) {
            context.getString(R.string.caller_call_log_ok)
        } else {
            context.getString(R.string.caller_need_call_log)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = store.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("openOutputStream failed")
                }
                status = context.getString(R.string.caller_backup_ok)
            }.onFailure { status = it.message ?: "backup failed" }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val body = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("read failed")
                }
                val parsed = BlocklistFormat.parse(body)
                store.mergeImport(parsed)
                status = context.getString(R.string.caller_batch_ok, parsed.size)
            }.onFailure { status = it.message ?: "import file failed" }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val body = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("read failed")
                }
                val parsed = BlocklistFormat.parse(body)
                store.replaceAll(parsed)
                status = context.getString(R.string.caller_restore_ok, parsed.size)
            }.onFailure { status = it.message ?: "restore failed" }
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
            roleHeld = true
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
        TextButton(onClick = onBack) {
            Text("← ${stringResource(R.string.caller_title)}")
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.caller_hero_eyebrow),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                        Text(
                            stringResource(R.string.caller_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.caller_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
                        )
                        Text(
                            if (roleHeld) {
                                stringResource(R.string.caller_status_ready)
                            } else {
                                stringResource(R.string.caller_status_need_role)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (!roleHeld) {
                            Button(
                                onClick = { requestScreeningRole() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.caller_set_default))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { requestScreeningRole() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.caller_role_held))
                            }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            status,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            item {
                CallerSection(title = stringResource(R.string.caller_setup_title)) {
                    Text(
                        stringResource(R.string.caller_gap_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { openDefaultApps() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.caller_open_defaults))
                    }
                    OutlinedButton(
                        onClick = {
                            if (callLogGranted) {
                                status = context.getString(R.string.caller_call_log_ok)
                            } else {
                                callLogLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (callLogGranted) {
                                stringResource(R.string.caller_call_log_ok)
                            } else {
                                stringResource(R.string.caller_need_call_log)
                            },
                        )
                    }
                }
            }

            item {
                CallerSection(title = stringResource(R.string.caller_add_title)) {
                    Text(
                        stringResource(R.string.caller_action_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val labels = listOf(
                            stringResource(R.string.caller_action_label),
                            stringResource(R.string.caller_action_block),
                            stringResource(R.string.caller_action_allow),
                        )
                        labels.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = ruleAction == index,
                                onClick = { ruleAction = index },
                                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                            ) {
                                Text(label)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.caller_number_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    OutlinedTextField(
                        value = ruleTag,
                        onValueChange = { ruleTag = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.caller_tag_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = asPrefix,
                            onClick = {
                                asPrefix = !asPrefix
                                if (asPrefix) asTag = false
                            },
                            label = { Text(stringResource(R.string.caller_prefix)) },
                        )
                        FilterChip(
                            selected = asTag,
                            onClick = {
                                asTag = !asTag
                                if (asTag) asPrefix = false
                            },
                            label = { Text(stringResource(R.string.caller_tag_mode_short)) },
                        )
                    }
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
                            if (ruleAction == 0 && ruleTag.isBlank() && !asTag) {
                                Toast.makeText(context, R.string.caller_label_need_tag, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val kind = when (ruleAction) {
                                2 -> CallRuleKind.ALLOW
                                1 -> CallRuleKind.BLOCK
                                else -> CallRuleKind.LABEL
                            }
                            scope.launch {
                                store.upsert(
                                    CallRule(
                                        id = UUID.randomUUID().toString(),
                                        pattern = pattern,
                                        kind = kind,
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
            }

            item {
                CallerSection(title = stringResource(R.string.caller_lookup_title)) {
                    OutlinedTextField(
                        value = lookupNumber,
                        onValueChange = { lookupNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.caller_lookup_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val r = store.lookup(lookupNumber)
                                val decisionText = when (r.decision) {
                                    NumberMatcher.Decision.BLOCK ->
                                        context.getString(R.string.caller_lookup_decision_block)
                                    NumberMatcher.Decision.ALLOW_LIST ->
                                        context.getString(R.string.caller_lookup_decision_allow)
                                    NumberMatcher.Decision.ALLOW_UNKNOWN ->
                                        context.getString(R.string.caller_lookup_decision_pass)
                                }
                                val label = r.matchedRules
                                    .firstOrNull { it.kind == CallRuleKind.ALLOW }
                                    ?: r.matchedRules.firstOrNull { it.kind == CallRuleKind.LABEL }
                                    ?: r.matchedRules.firstOrNull { it.kind == CallRuleKind.BLOCK }
                                val tagPart = label?.tag?.takeIf { it.isNotBlank() }
                                    ?: r.tags.firstOrNull()
                                lookupResult = buildString {
                                    append(decisionText)
                                    if (!tagPart.isNullOrBlank()) {
                                        append(" · ")
                                        append(context.getString(R.string.caller_lookup_label_fmt, tagPart))
                                    } else if (r.matchedRules.isEmpty()) {
                                        append(" · ")
                                        append(context.getString(R.string.caller_lookup_no_label))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.caller_lookup))
                    }
                    if (lookupResult.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                lookupResult,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            item {
                CallerSection(title = stringResource(R.string.caller_import_title)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.caller_import_hint)) },
                        minLines = 3,
                        shape = MaterialTheme.shapes.large,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                                        status = context.getString(R.string.caller_batch_ok, parsed.size)
                                    }.onFailure { status = it.message ?: "parse failed" }
                                }.onFailure {
                                    status = context.getString(R.string.caller_fetch_fail, it.message ?: "")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.caller_batch_cdn))
                    }
                    OutlinedButton(
                        onClick = { importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.caller_batch_file)) }
                    Text(
                        stringResource(R.string.caller_backup_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("onecaller-backup.json") },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.caller_backup)) }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.caller_restore)) }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.caller_rules_title, rules.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (rules.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            stringResource(R.string.caller_rules_empty),
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(rules, key = { it.id }) { rule ->
                CallerRuleCard(
                    rule = rule,
                    onRemove = { scope.launch { store.remove(rule.id) } },
                )
            }

            item {
                OutlinedButton(onClick = onOpenRecorder, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.caller_open_recorder))
                }
            }
        }
    }
}

@Composable
private fun CallerSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun CallerRuleCard(
    rule: CallRule,
    onRemove: () -> Unit,
) {
    val kindLabel = when (rule.kind) {
        CallRuleKind.LABEL -> stringResource(R.string.caller_action_label)
        CallRuleKind.BLOCK -> stringResource(R.string.caller_action_block)
        CallRuleKind.ALLOW -> stringResource(R.string.caller_action_allow)
    }
    val modeLabel = when (rule.mode) {
        CallMatchMode.EXACT -> stringResource(R.string.caller_mode_exact)
        CallMatchMode.PREFIX -> stringResource(R.string.caller_mode_prefix)
        CallMatchMode.TAG -> stringResource(R.string.caller_mode_tag)
    }
    val accent = when (rule.kind) {
        CallRuleKind.LABEL -> MaterialTheme.colorScheme.secondaryContainer
        CallRuleKind.BLOCK -> MaterialTheme.colorScheme.errorContainer
        CallRuleKind.ALLOW -> MaterialTheme.colorScheme.primaryContainer
    }
    val onAccent = when (rule.kind) {
        CallRuleKind.LABEL -> MaterialTheme.colorScheme.onSecondaryContainer
        CallRuleKind.BLOCK -> MaterialTheme.colorScheme.onErrorContainer
        CallRuleKind.ALLOW -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = accent,
            ) {
                Text(
                    kindLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = onAccent,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    rule.pattern,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        append(modeLabel)
                        if (rule.tag.isNotBlank()) {
                            append(" · ")
                            append(rule.tag)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.caller_remove))
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
        runCatching { return fetchBlocklist(url) }.onFailure { last = it }
    }
    throw last ?: IllegalStateException("no blocklist url")
}
