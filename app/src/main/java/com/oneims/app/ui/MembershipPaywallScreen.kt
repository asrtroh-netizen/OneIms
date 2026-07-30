package com.oneims.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneims.app.R

/**
 * 会员方案预览页（支付通道接入前仅 UI）。
 * 补回缺失实现，解除 MainActivity 编译阻塞；不改变「核心通话能力始终免费」产品边界。
 */
@Composable
fun MembershipPaywallScreen(
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    OneImsPage(
        title = stringResource(R.string.membership_title),
        subtitle = stringResource(R.string.membership_subtitle),
    ) {
        item {
            SectionBlock(title = stringResource(R.string.membership_pick_plan_title)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.membership_core_free_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.membership_plus_name) +
                            " · " + stringResource(R.string.membership_plus_price),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.membership_pro_name) +
                            " · " + stringResource(R.string.membership_pro_price),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.membership_payment_pending_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OneImsPrimaryButton(
                        text = stringResource(R.string.membership_cta_pro),
                        onClick = onPurchase,
                    )
                    TextButton(onClick = onRestore) {
                        Text(stringResource(R.string.membership_restore))
                    }
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.membership_later))
                    }
                }
            }
        }
    }
}
