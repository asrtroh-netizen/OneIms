package com.oneims.app.ui

import androidx.compose.ui.graphics.Color

/**
 * 邻仓 `_forks/thedjchi-Shizuku` `manager/src/main/res/values/colors.xml` 的首页色板真源。
 * 独立版首页组件只读这里，避免被动态取色带偏。
 */
object ShizukuHomePalette {
    val accent = Color(0xFF0B57D0)

    val heroInactiveBg = Color(0xFFF9DEDC)
    val heroInactiveFg = Color(0xFF410E0B)
    val heroActivatingBg = Color(0xFFD3E3FD)
    val heroActivatingFg = Color(0xFF041E49)
    val heroReadyBg = Color(0xFFFFFFFF)
    val heroReadyFg = Color(0xFF1A1B20)

    /** 对应 Shizuku `?colorSurfaceContainerHigh` 亮色观感。 */
    val tileSurface = Color(0xFFE8E8EF)
}
