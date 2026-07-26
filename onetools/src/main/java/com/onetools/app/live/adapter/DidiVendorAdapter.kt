package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate

object DidiVendorAdapter : VendorAdapter {
    override val source: LiveStatusSource = LiveStatusSource.DIDI

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank()) return AdapterOutcome.Ignored
        if (marketingNoise(joined)) return AdapterOutcome.Ignored
        val eta = extractEtaMinutes(joined) ?: 3
        val waiting = joined.contains("等待") || joined.contains("呼叫") || joined.contains("匹配")
        val primary = if (waiting) "等待接驾" else "行程中"
        val plate = extractPlate(joined) ?: "车牌待识别"
        val driver = Regex("司机([\\u4e00-\\u9fa5A-Za-z]{1,8})")
            .find(joined)?.groupValues?.getOrNull(1)?.let { "师傅$it" }
            ?: "司机师傅"
        val slots = CapsuleContentSlots(
            iconGlyph = "滴",
            primary = primary,
            secondary = "${eta}分钟",
            detailRows = listOf(
                "司机" to driver,
                "车牌" to plate,
                "预计" to "${eta} 分钟",
            ),
            actions = listOf("分享行程", "联系司机"),
        )
        if (!snippet.isOngoing && !joined.contains("行程") && !joined.contains("接驾") && !joined.contains("司机")) {
            return AdapterOutcome.Ignored
        }
        val session = sessionFromSlots(
            id = "live-didi-${snippet.key}",
            source = source,
            slots = slots,
            title = "滴滴出行 · $primary",
            subtitle = (snippet.text ?: snippet.title ?: "").take(48),
            template = CapsuleExpandTemplate.DETAIL_CARD,
            accent = 0xFFFF7043.toInt(),
        )
        return AdapterOutcome.Accepted(session, if (snippet.isOngoing) 0.85f else 0.5f)
    }
}
