package com.onetools.app.live.capsule

import com.onetools.app.live.LiveStatusSource

/**
 * 内容槽位契约：岛 UI 只吃槽位，不直接啃原始通知。
 */
data class CapsuleContentSlots(
    val iconGlyph: String,
    val primary: String,
    val secondary: String? = null,
    val stages: List<CapsuleStage> = emptyList(),
    val activeStageIndex: Int = 0,
    val detailRows: List<Pair<String, String>> = emptyList(),
    val actions: List<String> = emptyList(),
) {
    fun pillText(): String =
        if (secondary.isNullOrBlank()) primary else "$primary · $secondary"
}

fun LiveStatusSource.iconGlyph(): String = chipPrefix

fun CapsuleSession.toSlots(): CapsuleContentSlots = CapsuleContentSlots(
    iconGlyph = source.iconGlyph(),
    primary = pillPrimary,
    secondary = pillSecondary,
    stages = stages,
    activeStageIndex = activeStageIndex,
    detailRows = detailRows,
    actions = listOfNotNull(actionSecondary, actionPrimary),
)

fun CapsuleSession.withSlots(slots: CapsuleContentSlots): CapsuleSession = copy(
    pillPrimary = slots.primary,
    pillSecondary = slots.secondary,
    stages = slots.stages,
    activeStageIndex = slots.activeStageIndex,
    detailRows = slots.detailRows,
    actionPrimary = slots.actions.getOrNull(slots.actions.lastIndex),
    actionSecondary = slots.actions.getOrNull(0)?.takeIf { slots.actions.size > 1 },
)

/**
 * 扁胶囊展示策略（对齐超级岛/流体云心智）：
 * - 有 ETA/时间：短=logo+时间，长=logo+状态+时间
 * - 无时间（取件码/纯状态）：只短胶囊，双击仍可进大框
 */
data class CapsulePillFace(
    val primary: String,
    val secondary: String?,
    val allowsLong: Boolean,
)

fun CapsuleSession.allowsLongPill(): Boolean =
    !pillSecondary.isNullOrBlank() && looksLikeTimeMetric(pillSecondary)

fun CapsuleSession.pillFace(size: CapsulePillSize): CapsulePillFace {
    val allowsLong = allowsLongPill()
    val effective = if (allowsLong) size else CapsulePillSize.SHORT
    return when {
        !allowsLong -> CapsulePillFace(
            primary = pillPrimary,
            secondary = pillSecondary?.takeIf { it.isNotBlank() },
            allowsLong = false,
        )
        effective == CapsulePillSize.SHORT -> CapsulePillFace(
            primary = "",
            secondary = pillSecondary,
            allowsLong = true,
        )
        else -> CapsulePillFace(
            primary = pillPrimary,
            secondary = pillSecondary,
            allowsLong = true,
        )
    }
}

internal fun looksLikeTimeMetric(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty()) return false
    return t.contains("分钟") ||
        t.contains("小时") ||
        Regex("^\\d{1,3}\\s*分$").containsMatchIn(t) ||
        Regex("^\\d{1,2}:\\d{2}$").containsMatchIn(t)
}
