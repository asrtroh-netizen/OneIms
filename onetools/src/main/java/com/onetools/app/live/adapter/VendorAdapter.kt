package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate
import com.onetools.app.live.capsule.CapsuleSession
import com.onetools.app.live.capsule.CapsuleStage

data class NotificationSnippet(
    val packageName: String,
    val key: String,
    val title: String?,
    val text: String?,
    val isOngoing: Boolean,
)

sealed class AdapterOutcome {
    data class Accepted(val session: CapsuleSession, val confidence: Float) : AdapterOutcome()
    data object Ignored : AdapterOutcome()
}

interface VendorAdapter {
    val source: LiveStatusSource
    fun parse(snippet: NotificationSnippet): AdapterOutcome
}

object VendorAdapterRegistry {
    private val adapters: List<VendorAdapter> = listOf(
        MeituanVendorAdapter,
        DidiVendorAdapter,
        CainiaoVendorAdapter,
    )

    fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val source = LiveStatusSource.fromPackage(snippet.packageName) ?: return AdapterOutcome.Ignored
        val adapter = adapters.firstOrNull { it.source == source } ?: return AdapterOutcome.Ignored
        return adapter.parse(snippet)
    }
}

internal fun extractEtaMinutes(joined: String): Int? =
    Regex("(\\d{1,3})\\s*分钟").find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Regex("预计\\s*(\\d{1,3})").find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal fun extractPlate(joined: String): String? =
    Regex("([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][·.\\s]?[A-Z0-9]{4,6})")
        .find(joined)?.groupValues?.getOrNull(1)
        ?: Regex("([A-Z]{1,2}[·.\\s]?[A-Z0-9]{4,6})").find(joined)?.groupValues?.getOrNull(1)

internal fun marketingNoise(joined: String): Boolean {
    val keys = listOf("红包", "优惠", "领券", "立减", "秒杀", "广告", "邀请有礼")
    return keys.any { joined.contains(it) } &&
        !joined.contains("配送") &&
        !joined.contains("行程") &&
        !joined.contains("派送") &&
        !joined.contains("骑手") &&
        !joined.contains("司机")
}

internal fun sessionFromSlots(
    id: String,
    source: LiveStatusSource,
    slots: CapsuleContentSlots,
    title: String,
    subtitle: String,
    template: CapsuleExpandTemplate,
    accent: Int,
): CapsuleSession = CapsuleSession(
    id = id,
    source = source,
    pillPrimary = slots.primary,
    pillSecondary = slots.secondary,
    title = title,
    subtitle = subtitle,
    stages = slots.stages,
    activeStageIndex = slots.activeStageIndex,
    detailRows = slots.detailRows,
    actionPrimary = slots.actions.getOrNull(slots.actions.lastIndex),
    actionSecondary = slots.actions.getOrNull(0)?.takeIf { slots.actions.size > 1 },
    expandTemplate = template,
    accentColor = accent,
)

internal fun stageList(labels: List<String>, active: Int): List<CapsuleStage> =
    labels.mapIndexed { i, label -> CapsuleStage(label, done = i <= active) }
