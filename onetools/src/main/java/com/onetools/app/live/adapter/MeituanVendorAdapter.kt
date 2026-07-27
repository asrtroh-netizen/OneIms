package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate

/**
 * 美团外卖完整状态链（对齐超级岛/流体云：有 ETA 才出长胶囊）。
 * 阶段：已下单 → 已出餐 → 配送中 → 已送达
 */
object MeituanVendorAdapter : VendorAdapter {
    override val source: LiveStatusSource = LiveStatusSource.MEITUAN

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank()) return AdapterOutcome.Ignored
        if (marketingNoise(joined)) return AdapterOutcome.Ignored
        val eta = extractEtaMinutes(joined)
        // 终态优先：避免「骑手已送达」被「骑手」误判成配送中。
        // 刚下单常见文案（支付成功/订单已提交）往往不是 FLAG_ONGOING，不能直接 Ignored。
        val stage = when {
            joined.contains("已送达") || joined.contains("已完成") ||
                (joined.contains("送达") && !joined.contains("预计")) -> 3
            joined.contains("配送") || joined.contains("骑手") || joined.contains("送餐") ||
                joined.contains("取餐") || joined.contains("派送") -> 2
            joined.contains("出餐") || joined.contains("制作") || joined.contains("备餐") -> 1
            joined.contains("下单") || joined.contains("已接单") || joined.contains("商家") ||
                joined.contains("支付") || joined.contains("已提交") || joined.contains("确认中") ||
                joined.contains("等待商家") -> 0
            eta != null -> 2
            snippet.isOngoing -> 2
            // 非 ongoing：仍认「订单已/成功/等待…」类瞬时通知
            joined.contains("订单") && (
                joined.contains("成功") || joined.contains("已") || joined.contains("等待")
            ) -> 0
            else -> return AdapterOutcome.Ignored
        }
        val primary = when (stage) {
            3 -> "已送达"
            2 -> "配送中"
            1 -> "已出餐"
            else -> "已下单"
        }
        // 无时间字段 → 只短胶囊（logo+状态）；有 ETA → 短=时间 / 长=状态+时间。
        val secondary = if (stage >= 3) null else eta?.let { "${it}分钟" }
        val slots = CapsuleContentSlots(
            iconGlyph = "美",
            primary = primary,
            secondary = secondary,
            stages = stageList(listOf("已下单", "已出餐", "配送中", "已送达"), stage),
            activeStageIndex = stage,
            detailRows = buildList {
                add("状态" to primary)
                if (eta != null) add("预计" to "${eta}分钟")
            },
            actions = listOf("查看订单"),
        )
        val session = sessionFromSlots(
            id = "live-meituan-${snippet.key}",
            source = source,
            slots = slots,
            title = "美团外卖 · $primary",
            subtitle = (snippet.text ?: snippet.title ?: "").take(48),
            template = CapsuleExpandTemplate.PROGRESS_CARD,
            accent = 0xFFFFC107.toInt(),
        )
        val confidence = if (snippet.isOngoing) 0.85f else 0.6f
        return AdapterOutcome.Accepted(session, confidence)
    }
}
