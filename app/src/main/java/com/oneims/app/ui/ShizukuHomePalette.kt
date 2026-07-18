package com.oneims.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 邻仓 `_forks/thedjchi-Shizuku` 首页色板（硬色）。
 * 用 surface 亮度判断明暗，跟 App 内主题开关一致（不单看系统 isSystemInDarkTheme）。
 */
object ShizukuHomePalette {
    /** Shizuku `app_color_light` / 截图「启动」钮。 */
    val accent = Color(0xFF0B57D0)
    val onAccent = Color.White

    val heroInactiveBg = Color(0xFFF9DEDC)
    val heroInactiveFg = Color(0xFF410E0B)
    val heroActivatingBg = Color(0xFFD3E3FD)
    val heroActivatingFg = Color(0xFF041E49)
    val heroReadyBg = Color(0xFFFFFFFF)
    val heroReadyFg = Color(0xFF1A1B20)

    private val tileSurfaceLight = Color(0xFFE8E8EF)
    private val tileSurfaceDark = Color(0xFF2B2930)
    private val tileContentLight = Color(0xFF1A1B20)
    private val tileContentDark = Color(0xFFE2E2E9)

    @Composable
    private fun isDarkUi(): Boolean =
        MaterialTheme.colorScheme.surface.luminance() < 0.5f

    @Composable
    fun tileSurface(): Color =
        if (isDarkUi()) tileSurfaceDark else tileSurfaceLight

    @Composable
    fun tileContent(): Color =
        if (isDarkUi()) tileContentDark else tileContentLight
}
