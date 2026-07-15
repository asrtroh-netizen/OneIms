package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneims.app.R
import com.oneims.app.core.CarrierProfiles
import com.oneims.app.core.formatCarrierShortName
import com.oneims.app.model.SimInfo

/**
 * 运营商推荐方案卡：原功能页「移动网络」首卡，现供首页复用。
 */
@Composable
fun CarrierRecommendCard(
    sims: List<SimInfo>,
    selectedSim: SimInfo?,
    actionsEnabled: Boolean,
    applying: Boolean,
    onApplyRecommended: () -> Unit,
) {
    SectionBlock(
        title = stringResource(R.string.mobile_network_title),
        description = stringResource(R.string.mobile_network_subtitle),
    ) {
        if (sims.isEmpty()) {
            SettingsActionRow(
                icon = Icons.Filled.AccountBox,
                title = stringResource(R.string.no_sim_hint),
                subtitle = stringResource(R.string.no_sim_detail),
                onClick = null,
            )
        } else {
            selectedSim?.let { sim ->
                val profile = CarrierProfiles.match(sim.mcc, sim.mnc)
                val context = LocalContext.current
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.system_target_preview,
                            sim.slotIndex + 1,
                            formatCarrierShortName(sim.carrierName),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        profile.name(context),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        profile.note(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneImsPrimaryButton(
                        text = stringResource(R.string.apply_recommended),
                        onClick = onApplyRecommended,
                        enabled = actionsEnabled,
                        loading = applying,
                        loadingText = stringResource(R.string.action_applying),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
