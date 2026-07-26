package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate

object MeituanVendorAdapter : VendorAdapter {
    override val source: LiveStatusSource = LiveStatusSource.MEITUAN

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank()) return AdapterOutcome.Ignored
        if (marketingNoise(joined)) return AdapterOutcome.Ignored
        val eta = extractEtaMinutes(joined) ?: 18
        val stage = when {
            // 「预计xx送达」仍属配送中，必须先匹配配送/骑手再匹配终态。
            joined.contains("配送") || joined.contains("骑手") || joined.contains("送餐") -> 2
            joined.contains("已送达") || joined.contains("已完成") ||
                (joined.contains("送达") && !joined.contains("预计")) -> 3
            joined.contains("出餐") || joined.contains("制作") || joined.contains("备餐") -> 1
            joined.contains("下单") || joined.contains("已接单") -> 0
            else -> if (snippet.isOngoing) 2 else return AdapterOutcome.Ignored
        }
        val primary = when (stage) {
            3 -> "已送达"
            2 -> "配送中"
            1 -> "已出餐"
            else -> "已下单"
        }
        val slots = CapsuleContentSlots(
            iconGlyph = "美",
            primary = primary,
            secondary = "${eta}分钟",
            stages = stageList(listOf("已下单", "已出餐", "配送中", "已送达"), stage),
            activeStageIndex = stage,
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
        val confidence = if (snippet.isOngoing) 0.8f else 0.55f
        return AdapterOutcome.Accepted(session, confidence)
    }
}
