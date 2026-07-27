package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate

/**
 * 滴滴行程状态链：等待接驾 → 司机赶来 → 行程中 → 已到达。
 * 有 ETA 才允许短/长胶囊切换；无时间只短胶囊。
 */
object DidiVendorAdapter : VendorAdapter {
    override val source: LiveStatusSource = LiveStatusSource.DIDI

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank()) return AdapterOutcome.Ignored
        if (marketingNoise(joined)) return AdapterOutcome.Ignored
        val eta = extractEtaMinutes(joined)
        val tripSignal = joined.contains("行程") || joined.contains("接驾") ||
            joined.contains("司机") || joined.contains("上车") || joined.contains("到达") ||
            joined.contains("呼叫") || joined.contains("匹配") || joined.contains("叫车") ||
            joined.contains("派单") || joined.contains("应答") || joined.contains("快车") ||
            joined.contains("专车") || joined.contains("拼车") || joined.contains("滴滴") ||
            eta != null || extractPlate(joined) != null
        if (!snippet.isOngoing && !tripSignal) return AdapterOutcome.Ignored

        val stage = when {
            joined.contains("已完成") || joined.contains("行程结束") ||
                (joined.contains("已到达") && !joined.contains("司机已到达")) ||
                joined.contains("已下车") -> 3
            joined.contains("行程中") || joined.contains("前往目的地") ||
                joined.contains("行驶") || joined.contains("送你") -> 2
            joined.contains("司机已到达") || joined.contains("已到达上车点") ||
                joined.contains("车辆已到") -> 1
            joined.contains("等待") || joined.contains("呼叫") || joined.contains("匹配") ||
                joined.contains("正在为您") || joined.contains("叫车") || joined.contains("派单") ||
                joined.contains("应答") -> 0
            joined.contains("接驾") || joined.contains("赶来") || joined.contains("已接单") ||
                joined.contains("司机") -> 1
            eta != null -> 2
            snippet.isOngoing -> 2
            tripSignal -> 0
            else -> return AdapterOutcome.Ignored
        }
        val primary = when (stage) {
            3 -> "已到达"
            2 -> "行程中"
            1 -> "司机赶来"
            else -> "等待接驾"
        }
        val plate = extractPlate(joined)
        val driver = Regex("司机([\\u4e00-\\u9fa5A-Za-z]{1,8})")
            .find(joined)?.groupValues?.getOrNull(1)?.let { "${it}师傅" }
            ?: Regex("([\\u4e00-\\u9fa5]{1,4})师傅").find(joined)?.groupValues?.getOrNull(1)
                ?.let { "${it}师傅" }
        val secondary = if (stage >= 3) null else eta?.let { "${it}分钟" }
        val slots = CapsuleContentSlots(
            iconGlyph = "滴",
            primary = primary,
            secondary = secondary,
            stages = stageList(listOf("等待接驾", "司机赶来", "行程中", "已到达"), stage),
            activeStageIndex = stage,
            detailRows = buildList {
                add("状态" to primary)
                if (eta != null) add("预计" to "${eta}分钟")
                if (driver != null) add("司机" to driver)
                if (plate != null) add("车牌" to plate)
            },
            actions = listOf("分享行程", "联系司机"),
        )
        val session = sessionFromSlots(
            id = "live-didi-${snippet.key}",
            source = source,
            slots = slots,
            title = "滴滴出行 · $primary",
            subtitle = (snippet.text ?: snippet.title ?: "").take(48),
            template = CapsuleExpandTemplate.DETAIL_CARD,
            accent = 0xFFFF7043.toInt(),
        )
        return AdapterOutcome.Accepted(session, if (snippet.isOngoing) 0.85f else 0.55f)
    }
}
