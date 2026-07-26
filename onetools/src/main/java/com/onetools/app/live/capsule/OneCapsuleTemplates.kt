package com.onetools.app.live.capsule

import com.onetools.app.live.LiveStatusSource

/**
 * 概念图对齐的演示 / 归一化模板。
 * 颜色用 ARGB 整型，避免 JVM 单测依赖 android.graphics.Color。
 */
object OneCapsuleTemplates {
    private const val ACCENT_MEITUAN = 0xFFFFC107.toInt()
    private const val ACCENT_DIDI = 0xFFFF7043.toInt()
    private const val ACCENT_CAINIAO = 0xFF42A5F5.toInt()

    fun meituanDelivering(
        etaMinutes: Int = 18,
        stageIndex: Int = 2,
    ): CapsuleSession {
        val stages = listOf(
            CapsuleStage("已下单", true),
            CapsuleStage("已出餐", true),
            CapsuleStage("配送中", stageIndex >= 2),
            CapsuleStage("已送达", stageIndex >= 3),
        )
        return CapsuleSession(
            id = "demo-meituan",
            source = LiveStatusSource.MEITUAN,
            pillPrimary = "配送中",
            pillSecondary = "${etaMinutes}分钟",
            title = "美团外卖 · 配送中",
            subtitle = "骑手正在赶来 · 预计 $etaMinutes 分钟",
            stages = stages,
            activeStageIndex = stageIndex.coerceIn(0, stages.lastIndex),
            expandTemplate = CapsuleExpandTemplate.PROGRESS_CARD,
            accentColor = ACCENT_MEITUAN,
        )
    }

    fun didiOnTrip(
        etaMinutes: Int = 3,
        plate: String = "粤B·D1234",
        driver: String = "师傅张",
    ): CapsuleSession {
        return CapsuleSession(
            id = "demo-didi",
            source = LiveStatusSource.DIDI,
            pillPrimary = "行程中",
            pillSecondary = "${etaMinutes}分钟",
            title = "滴滴出行 · 行程中",
            subtitle = "司机已接单，预计 $etaMinutes 分钟到达",
            detailRows = listOf(
                "司机" to driver,
                "车牌" to plate,
                "预计" to "${etaMinutes} 分钟",
            ),
            actionPrimary = "联系司机",
            actionSecondary = "分享行程",
            expandTemplate = CapsuleExpandTemplate.DETAIL_CARD,
            accentColor = ACCENT_DIDI,
        )
    }

    fun cainiaoParcel(status: String = "派送中"): CapsuleSession {
        return CapsuleSession(
            id = "demo-cainiao",
            source = LiveStatusSource.CAINIAO,
            pillPrimary = status,
            pillSecondary = "包裹",
            title = "菜鸟 · $status",
            subtitle = "快递正在派送，请留意电话",
            stages = listOf(
                CapsuleStage("已揽收", true),
                CapsuleStage("运输中", true),
                CapsuleStage("派送中", status.contains("派")),
                CapsuleStage("已签收", status.contains("签")),
            ),
            activeStageIndex = 2,
            expandTemplate = CapsuleExpandTemplate.PROGRESS_CARD,
            accentColor = ACCENT_CAINIAO,
        )
    }

    /** 从通知文本尽量结构化；优先走厂商适配器。 */
    fun fromNotification(
        source: LiveStatusSource,
        title: String?,
        text: String?,
        chipFallback: String,
    ): CapsuleSession {
        val pkg = source.packages.firstOrNull().orEmpty()
        val outcome = com.onetools.app.live.adapter.VendorAdapterRegistry.parse(
            com.onetools.app.live.adapter.NotificationSnippet(
                packageName = pkg,
                key = "tpl-${source.id}",
                title = title ?: chipFallback,
                text = text,
                isOngoing = true,
            ),
        )
        if (outcome is com.onetools.app.live.adapter.AdapterOutcome.Accepted) {
            return outcome.session
        }
        return CapsuleSession(
            id = "live-${source.id}",
            source = source,
            pillPrimary = chipFallback.take(8).ifBlank { source.labelZh },
            pillSecondary = null,
            title = source.labelZh,
            subtitle = (text ?: title ?: chipFallback).take(48),
            expandTemplate = CapsuleExpandTemplate.DETAIL_CARD,
            accentColor = 0xFF90A4AE.toInt(),
        )
    }
}
