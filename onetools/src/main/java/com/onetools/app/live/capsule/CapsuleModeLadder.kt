package com.onetools.app.live.capsule

/**
 * 干净室档位阶梯：对照 OneCapsule DOT/MINI/COMPACT/EXPANDED。
 * 旧 PILL ≡ COMPACT。
 */
object CapsuleModeLadder {
    val VISIBLE: List<CapsuleDisplayMode> = listOf(
        CapsuleDisplayMode.DOT,
        CapsuleDisplayMode.MINI,
        CapsuleDisplayMode.COMPACT,
        CapsuleDisplayMode.EXPANDED,
    )

    fun clampQuiet(mode: CapsuleDisplayMode): CapsuleDisplayMode = when (mode) {
        CapsuleDisplayMode.DOT,
        CapsuleDisplayMode.MINI,
        CapsuleDisplayMode.COMPACT,
        -> mode
        else -> CapsuleDisplayMode.COMPACT
    }

    fun stepUp(current: CapsuleDisplayMode): CapsuleDisplayMode {
        if (current == CapsuleDisplayMode.HIDDEN) return CapsuleDisplayMode.COMPACT
        val i = VISIBLE.indexOf(current)
        if (i < 0) return CapsuleDisplayMode.COMPACT
        return VISIBLE[(i + 1).coerceAtMost(VISIBLE.lastIndex)]
    }

    fun stepDown(current: CapsuleDisplayMode, floor: CapsuleDisplayMode): CapsuleDisplayMode {
        if (current == CapsuleDisplayMode.HIDDEN) return CapsuleDisplayMode.HIDDEN
        val floorIdx = VISIBLE.indexOf(clampQuiet(floor)).coerceAtLeast(0)
        val i = VISIBLE.indexOf(current).let { if (it < 0) floorIdx else it }
        return VISIBLE[(i - 1).coerceAtLeast(floorIdx)]
    }

    fun labelZh(mode: CapsuleDisplayMode): String = when (mode) {
        CapsuleDisplayMode.HIDDEN -> "隐藏"
        CapsuleDisplayMode.DOT -> "DOT · 圆点"
        CapsuleDisplayMode.MINI -> "MINI · 迷你"
        CapsuleDisplayMode.COMPACT -> "COMPACT · 胶囊"
        CapsuleDisplayMode.EXPANDED -> "EXPANDED · 展开"
    }
}
