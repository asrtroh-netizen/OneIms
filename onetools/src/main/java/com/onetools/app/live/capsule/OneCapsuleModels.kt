package com.onetools.app.live.capsule

import androidx.annotation.ColorInt
import com.onetools.app.live.LiveStatusSource

/** 展示层模式：隐藏 / 扁胶囊 / 展开卡 */
enum class CapsuleDisplayMode {
    HIDDEN,
    PILL,
    EXPANDED,
}

/** 展开模板：进度条卡（外卖）或关键详情（网约车） */
enum class CapsuleExpandTemplate {
    PROGRESS_CARD,
    DETAIL_CARD,
}

data class CapsuleStage(
    val label: String,
    val done: Boolean,
)

/**
 * 一条实时服务会话（可多条并存，左右滑动切换）。
 */
data class CapsuleSession(
    val id: String,
    val source: LiveStatusSource,
    val pillPrimary: String,
    val pillSecondary: String? = null,
    val title: String,
    val subtitle: String = "",
    val stages: List<CapsuleStage> = emptyList(),
    val activeStageIndex: Int = 0,
    val detailRows: List<Pair<String, String>> = emptyList(),
    val actionPrimary: String? = null,
    val actionSecondary: String? = null,
    val expandTemplate: CapsuleExpandTemplate = CapsuleExpandTemplate.PROGRESS_CARD,
    @ColorInt val accentColor: Int = 0xFFFFC107.toInt(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun pillText(): String =
        if (pillSecondary.isNullOrBlank()) pillPrimary
        else "$pillPrimary · $pillSecondary"
}

data class CapsuleUiSnapshot(
    val mode: CapsuleDisplayMode,
    val sessions: List<CapsuleSession>,
    val activeIndex: Int,
) {
    val active: CapsuleSession? =
        sessions.getOrNull(activeIndex.coerceIn(0, (sessions.size - 1).coerceAtLeast(0)))

    companion object {
        val Empty = CapsuleUiSnapshot(CapsuleDisplayMode.HIDDEN, emptyList(), 0)
    }
}
