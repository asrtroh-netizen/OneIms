package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import com.oneims.app.core.DodoPaySupportClient
import com.oneims.app.core.DodoPaySupportConfig
import com.oneims.app.core.SupportFeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SponsorScreen(
    onPublish: (String) -> Unit,
    pendingPaymentProof: String? = null,
    onPendingPaymentProofConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets = remember { DodoPaySupportClient.presetAmounts() }

    var selectedPreset by remember { mutableStateOf<Int?>(6) }
    var customAmount by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("匿名朋友") }
    var message by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var showThanks by remember { mutableStateOf(false) }
    var supporterUnlocked by remember {
        mutableStateOf(DodoPaySupportClient.isSupporterUnlocked(context))
    }
    var feedItems by remember { mutableStateOf<List<SupportFeedItem>>(emptyList()) }
    var feedError by remember { mutableStateOf(false) }
    var feedLoading by remember { mutableStateOf(false) }

    fun currentAmount(): Double? {
        val custom = customAmount.trim()
        if (custom.isNotEmpty()) return DodoPaySupportClient.parseAmount(custom)
        val preset = selectedPreset ?: return null
        return preset.toDouble()
    }

    fun verifyProof(proof: String) {
        if (verifying) return
        verifying = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DodoPaySupportClient.verifyDodopayPaymentProof(context, proof)
            }
            verifying = false
            if (result.success) {
                supporterUnlocked = true
                showThanks = true
            }
            onPublish(result.message)
        }
    }

    LaunchedEffect(pendingPaymentProof) {
        val proof = pendingPaymentProof ?: return@LaunchedEffect
        onPendingPaymentProofConsumed()
        verifyProof(proof)
    }

    LaunchedEffect(Unit) {
        if (!DodoPaySupportConfig.isFeedConfigured()) return@LaunchedEffect
        feedLoading = true
        val result = withContext(Dispatchers.IO) { DodoPaySupportClient.fetchSupportFeed() }
        feedLoading = false
        result.onSuccess { feedItems = it }.onFailure { feedError = true }
    }

    OneImsPage(
        title = stringResource(R.string.sponsor_title),
        subtitle = stringResource(R.string.sponsor_subtitle),
    ) {
        item {
            SectionBlock(title = stringResource(R.string.sponsor_intro_title)) {
                Text(
                    text = stringResource(R.string.sponsor_intro),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (supporterUnlocked) {
            item {
                InlineNotice(text = stringResource(R.string.sponsor_supporter_badge))
            }
        }

        item {
            SectionBlock(
                title = stringResource(R.string.sponsor_amount_title),
                description = stringResource(R.string.sponsor_amount_subtitle),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.take(3).forEach { amount ->
                            FilterChip(
                                selected = selectedPreset == amount && customAmount.isBlank(),
                                onClick = {
                                    selectedPreset = amount
                                    customAmount = ""
                                },
                                label = { Text("¥$amount") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.drop(3).forEach { amount ->
                            FilterChip(
                                selected = selectedPreset == amount && customAmount.isBlank(),
                                onClick = {
                                    selectedPreset = amount
                                    customAmount = ""
                                },
                                label = { Text("¥$amount") },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = {
                            customAmount = it
                            if (it.isNotBlank()) selectedPreset = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sponsor_custom_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            }
        }

        item {
            SectionBlock(title = stringResource(R.string.sponsor_profile_title)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sponsor_nickname_label)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sponsor_message_label)) },
                        placeholder = { Text(stringResource(R.string.sponsor_message_hint)) },
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            }
        }

        item {
            SectionBlock(title = stringResource(R.string.sponsor_channel_title)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sponsor_channel_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!DodoPaySupportConfig.isSupportUrlConfigured()) {
                        InlineNotice(
                            text = stringResource(R.string.sponsor_link_missing),
                            danger = true,
                        )
                    }
                    OneImsPrimaryButton(
                        text = if (verifying) {
                            stringResource(R.string.sponsor_verifying)
                        } else {
                            stringResource(R.string.sponsor_cta)
                        },
                        onClick = {
                            val amount = currentAmount()
                            if (amount == null) {
                                onPublish(context.getString(R.string.sponsor_amount_invalid))
                                return@OneImsPrimaryButton
                            }
                            if (!DodoPaySupportConfig.isSupportUrlConfigured()) {
                                onPublish(context.getString(R.string.sponsor_link_missing))
                                return@OneImsPrimaryButton
                            }
                            val url = DodoPaySupportClient.buildCheckoutUrl(
                                context = context,
                                amount = amount,
                                nickname = nickname,
                                message = message,
                            )
                            if (url.isNullOrBlank()) {
                                onPublish(context.getString(R.string.sponsor_link_missing))
                                return@OneImsPrimaryButton
                            }
                            val opened = DodoPaySupportClient.openCheckout(context, url)
                            onPublish(
                                context.getString(
                                    if (opened) {
                                        R.string.sponsor_opened_browser
                                    } else {
                                        R.string.sponsor_open_failed
                                    },
                                ),
                            )
                        },
                        enabled = !verifying,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            onPublish(context.getString(R.string.sponsor_refresh_hint))
                        },
                        enabled = !verifying,
                    ) {
                        Text(stringResource(R.string.sponsor_refresh_status))
                    }
                }
            }
        }

        if (DodoPaySupportConfig.isFeedConfigured()) {
            item {
                SectionBlock(title = stringResource(R.string.sponsor_feed_title)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when {
                            feedLoading -> Text(
                                text = stringResource(R.string.sponsor_feed_loading),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            feedError -> InlineNotice(
                                text = stringResource(R.string.sponsor_feed_failed),
                                danger = true,
                            )
                            feedItems.isEmpty() -> Text(
                                text = stringResource(R.string.sponsor_feed_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> feedItems.take(30).forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = buildString {
                                            append(item.nickname)
                                            item.amount?.let { append(" · ¥").append(it) }
                                            item.timeLabel?.let { append(" · ").append(it) }
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    if (item.message.isNotBlank()) {
                                        Text(
                                            text = item.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    item.authorReply?.let { reply ->
                                        Text(
                                            text = stringResource(
                                                R.string.sponsor_feed_author_reply,
                                                reply,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            InlineNotice(text = stringResource(R.string.sponsor_voluntary_notice))
        }
    }

    if (showThanks) {
        AlertDialog(
            onDismissRequest = { showThanks = false },
            title = { Text(stringResource(R.string.sponsor_thanks_title)) },
            text = { Text(stringResource(R.string.sponsor_thanks_body)) },
            confirmButton = {
                TextButton(onClick = { showThanks = false }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
        )
    }
}
