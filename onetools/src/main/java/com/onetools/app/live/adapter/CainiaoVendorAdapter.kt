package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate

object CainiaoVendorAdapter : VendorAdapter {
    override val source: LiveStatusSource = LiveStatusSource.CAINIAO

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank()) return AdapterOutcome.Ignored
        if (marketingNoise(joined)) return AdapterOutcome.Ignored
        val stage = when {
            joined.contains("签收") || joined.contains("已取") -> 3
            joined.contains("派送") || joined.contains("派件") || joined.contains("快递员") -> 2
            joined.contains("运输") || joined.contains("中转") || joined.contains("发往") -> 1
            joined.contains("揽收") || joined.contains("已收") -> 0
            else -> if (snippet.isOngoing) 2 else return AdapterOutcome.Ignored
        }
        val primary = when (stage) {
            3 -> "已签收"
            2 -> "派送中"
            1 -> "运输中"
            else -> "已揽收"
        }
        val slots = CapsuleContentSlots(
            iconGlyph = "菜",
            primary = primary,
            secondary = "包裹",
            stages = stageList(listOf("已揽收", "运输中", "派送中", "已签收"), stage),
            activeStageIndex = stage,
            actions = listOf("查看物流"),
        )
        val session = sessionFromSlots(
            id = "live-cainiao-${snippet.key}",
            source = source,
            slots = slots,
            title = "菜鸟 · $primary",
            subtitle = (snippet.text ?: snippet.title ?: "").take(48),
            template = CapsuleExpandTemplate.PROGRESS_CARD,
            accent = 0xFF42A5F5.toInt(),
        )
        return AdapterOutcome.Accepted(session, 0.75f)
    }
}
