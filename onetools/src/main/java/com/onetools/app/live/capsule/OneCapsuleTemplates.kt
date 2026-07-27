package com.onetools.app.live.capsule

import com.onetools.app.live.LiveStatusSource

/**
 * 概念图对齐的演示 / 归一化模板。
 * 颜色用 ARGB 整型，避免 JVM 单测依赖 android.graphics.Color。
 */
object OneCapsuleTemplates {
    private const val ACCENT_MEITUAN = 0xFFFF6A00.toInt()
    private const val ACCENT_DIDI = 0xFF00B87A.toInt()
    private const val ACCENT_CAINIAO = 0xFF1677FF.toInt()

    fun meituanDelivering(
        etaMinutes: Int = 12,
        stageIndex: Int = 1,
    ): CapsuleSession {
        val stages = listOf(
            CapsuleStage("商家接单", true),
            CapsuleStage("取餐", stageIndex >= 1),
            CapsuleStage("配送中", stageIndex >= 2),
            CapsuleStage("即将送达", stageIndex >= 3),
        )
        return CapsuleSession(
            id = "demo-meituan",
            source = LiveStatusSource.MEITUAN,
            pillPrimary = "骑手已接单",
            pillSecondary = "${etaMinutes}分钟",
            title = "美团外卖",
            subtitle = "骑手正在取餐 · 预计 12:40 送达",
            stages = stages,
            activeStageIndex = stageIndex.coerceIn(0, stages.lastIndex),
            expandTemplate = CapsuleExpandTemplate.PROGRESS_CARD,
            accentColor = ACCENT_MEITUAN,
        )
    }

    fun didiOnTrip(
        etaMinutes: Int = 3,
        plate: String = "粤B·8A23",
        driver: String = "王师傅",
    ): CapsuleSession {
        return CapsuleSession(
            id = "demo-didi",
            source = LiveStatusSource.DIDI,
            pillPrimary = "滴滴出行",
            pillSecondary = "${etaMinutes}分钟",
            title = "滴滴出行",
            subtitle = "司机已到达 · 距离你 2.1 km",
            detailRows = listOf(
                "距离" to "2.1 km",
                "状态" to "司机已到达",
                "司机" to driver,
                "车牌" to plate,
            ),
            actionPrimary = "联系司机",
            actionSecondary = "分享行程",
            expandTemplate = CapsuleExpandTemplate.DETAIL_CARD,
            accentColor = ACCENT_DIDI,
        )
    }

    fun cainiaoParcel(status: String = "派送中", pickupCode: String = "A3K9"): CapsuleSession {
        return CapsuleSession(
            id = "demo-cainiao",
            source = LiveStatusSource.CAINIAO,
            // 无 ETA：短胶囊只展示取件码；双击进大框看完整状态。
            pillPrimary = pickupCode,
            pillSecondary = null,
            title = "菜鸟 · $status",
            subtitle = "取件码 $pickupCode · 请留意柜机/电话",
            stages = listOf(
                CapsuleStage("已揽收", true),
                CapsuleStage("运输中", true),
                CapsuleStage("派送中", status.contains("派")),
                CapsuleStage("已签收", status.contains("签")),
            ),
            activeStageIndex = 2,
            detailRows = listOf("取件码" to pickupCode, "状态" to status),
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
