package com.onetools.app.live.capsule

/**
 * 内容槽位契约：岛 UI 只吃槽位，不直接啃原始通知。
 */
data class CapsuleContentSlots(
    /** 轻量图标占位（字母/短标），后续可换真实 Drawable。 */
    val iconGlyph: String,
    val primary: String,
    val secondary: String? = null,
    val stages: List<CapsuleStage> = emptyList(),
    val activeStageIndex: Int = 0,
    val detailRows: List<Pair<String, String>> = emptyList(),
    val actions: List<String> = emptyList(),
) {
    fun pillPrimary(): String = primary
    fun pillSecondary(): String? = secondary
    fun pillText(): String =
        if (secondary.isNullOrBlank()) primary else "$primary · $secondary"
}

fun CapsuleSession.toSlots(): CapsuleContentSlots = CapsuleContentSlots(
    iconGlyph = when (source) {
        com.onetools.app.live.LiveStatusSource.MEITUAN -> "美"
        com.onetools.app.live.LiveStatusSource.DIDI -> "滴"
        com.onetools.app.live.LiveStatusSource.CAINIAO -> "菜"
    },
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
