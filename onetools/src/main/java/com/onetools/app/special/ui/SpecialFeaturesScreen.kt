package com.onetools.app.special.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.onetools.app.special.display.FiveGDisplayController
import com.onetools.app.special.display.SignalBarController
import com.onetools.app.special.display.SpecialFeatureStore
import com.onetools.app.special.privilege.SpecialPrivilege
import com.onetools.app.special.sim.DataSimController
import com.onetools.app.special.sim.SpecialSimInfo
import com.onetools.app.special.sim.SpecialSimSwitchResult
import com.onetools.app.special.sim.TileHelper
import com.onetools.app.ui.OneToolsSettingsGroup
import com.onetools.app.ui.OneToolsToolPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpecialFeaturesScreen(
    onBack: () -> Unit,
    channelReady: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubId by remember { mutableStateOf(-1) }
    var sims by remember { mutableStateOf<List<SpecialSimInfo>>(emptyList()) }
    var signalMode by remember { mutableStateOf(SpecialFeatureStore.SignalBarMode.AUTO) }
    var fiveG by remember { mutableStateOf(SpecialFeatureStore.fiveGConfig(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun refreshSims() {
        val hasPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        sims = if (hasPhone) {
            runCatching { DataSimController.getActiveSims(context) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (selectedSubId < 0) {
            selectedSubId = sims.firstOrNull { it.isDefaultData }?.subId
                ?: sims.firstOrNull()?.subId
                ?: SubscriptionManager.getDefaultDataSubscriptionId().takeIf { it >= 0 }
                ?: -1
        }
        if (selectedSubId >= 0) {
            signalMode = SpecialFeatureStore.signalBarMode(context, selectedSubId)
        }
        fiveG = SpecialFeatureStore.fiveGConfig(context)
    }

    LaunchedEffect(Unit) { refreshSims() }

    OneToolsToolPage(
        title = stringResource(R.string.special_title),
        subtitle = stringResource(R.string.special_subtitle),
        onBack = onBack,
    ) {
        item {
            Text(
                text = if (channelReady && SpecialPrivilege.isReady()) {
                    stringResource(R.string.special_channel_ready)
                } else {
                    stringResource(R.string.special_channel_need)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SpecialSection(title = stringResource(R.string.special_signal_title)) {
                if (sims.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.special_target_sim,
                            (sims.firstOrNull { it.subId == selectedSubId }?.slotIndex ?: 0) + 1,
                            sims.firstOrNull { it.subId == selectedSubId }?.shortName ?: "—",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                SignalChoice(
                    title = stringResource(R.string.special_signal_auto),
                    selected = signalMode == SpecialFeatureStore.SignalBarMode.AUTO,
                    enabled = !busy,
                    onClick = { signalMode = SpecialFeatureStore.SignalBarMode.AUTO },
                )
                SignalChoice(
                    title = stringResource(R.string.special_signal_four),
                    selected = signalMode == SpecialFeatureStore.SignalBarMode.FOUR_BARS,
                    enabled = !busy,
                    onClick = { signalMode = SpecialFeatureStore.SignalBarMode.FOUR_BARS },
                )
                SignalChoice(
                    title = stringResource(R.string.special_signal_five),
                    selected = signalMode == SpecialFeatureStore.SignalBarMode.FIVE_BARS,
                    enabled = !busy,
                    onClick = { signalMode = SpecialFeatureStore.SignalBarMode.FIVE_BARS },
                )
                Button(
                    onClick = {
                        if (selectedSubId < 0) {
                            status = context.getString(R.string.special_data_switch_no_sim)
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) {
                                runCatching {
                                    SignalBarController.apply(context, selectedSubId, signalMode)
                                }.getOrElse { it.message ?: it.toString() }
                            }
                            status = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            busy = false
                        }
                    },
                    enabled = !busy && channelReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.special_signal_apply))
                }
            }
        }

        item {
            SpecialSection(title = stringResource(R.string.special_five_g_title)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.special_five_g_enable))
                    Switch(
                        checked = fiveG.enabled,
                        onCheckedChange = { fiveG = fiveG.copy(enabled = it) },
                        enabled = !busy,
                    )
                }
                listOf(
                    SpecialFeatureStore.FiveGConfig.Mode.CONSERVATIVE to R.string.special_five_g_mode_conservative,
                    SpecialFeatureStore.FiveGConfig.Mode.CN_SPEED to R.string.special_five_g_mode_cn,
                    SpecialFeatureStore.FiveGConfig.Mode.COOL to R.string.special_five_g_mode_cool,
                    SpecialFeatureStore.FiveGConfig.Mode.CUSTOM to R.string.special_five_g_mode_custom,
                ).forEach { (mode, labelRes) ->
                    SignalChoice(
                        title = stringResource(labelRes),
                        selected = fiveG.mode == mode,
                        enabled = !busy && fiveG.enabled,
                        onClick = { fiveG = fiveG.copy(mode = mode) },
                    )
                }
                if (fiveG.mode == SpecialFeatureStore.FiveGConfig.Mode.CUSTOM) {
                    OutlinedTextField(
                        value = fiveG.systemIconConfigString,
                        onValueChange = { fiveG = fiveG.copy(systemIconConfigString = it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        enabled = !busy && fiveG.enabled,
                        label = { Text(stringResource(R.string.special_five_g_custom_label)) },
                    )
                }
                Button(
                    onClick = {
                        if (selectedSubId < 0) {
                            status = context.getString(R.string.special_data_switch_no_sim)
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) {
                                runCatching {
                                    FiveGDisplayController.apply(context, selectedSubId, fiveG)
                                }.getOrElse { it.message ?: it.toString() }
                            }
                            fiveG = SpecialFeatureStore.fiveGConfig(context)
                            status = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            busy = false
                        }
                    },
                    enabled = !busy && channelReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.special_five_g_apply))
                }
            }
        }

        item {
            SpecialSection(title = stringResource(R.string.special_qs_feature_title)) {
                Text(
                    text = stringResource(R.string.special_qs_feature_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                TextButton(
                    onClick = { TileHelper.openTileEditor(context) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.special_qs_open_editor))
                }
                sims.forEach { sim ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy && !sim.isDefaultData && channelReady) {
                                busy = true
                                scope.launch {
                                    val result = DataSimController.switchDefaultDataSubId(
                                        context,
                                        sim.subId,
                                    )
                                    val msg = when (result) {
                                        is SpecialSimSwitchResult.Success ->
                                            result.warning
                                                ?: context.getString(
                                                    R.string.special_data_switch_success,
                                                    sim.slotIndex + 1,
                                                )
                                        is SpecialSimSwitchResult.Failed ->
                                            context.getString(
                                                R.string.special_data_switch_failed,
                                                result.reason,
                                            )
                                    }
                                    status = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    refreshSims()
                                    busy = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(
                                R.string.special_data_switch_row,
                                sim.slotIndex + 1,
                                sim.shortName,
                                if (sim.isDefaultData) {
                                    stringResource(R.string.special_data_switch_current)
                                } else {
                                    ""
                                },
                            ),
                        )
                    }
                }
                if (sims.isEmpty()) {
                    Text(
                        text = stringResource(R.string.special_data_switch_no_sim),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        status?.let { msg ->
            item {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SpecialSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        OneToolsSettingsGroup(content = content)
    }
}

@Composable
private fun SignalChoice(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
