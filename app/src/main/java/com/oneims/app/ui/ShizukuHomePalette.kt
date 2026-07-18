package com.oneims.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 邻仓 `_forks/thedjchi-Shizuku` 首页色板。
 * - Hero 三态：固定色（就绪白卡在暗色底上仍为白卡，对齐截图）。
 * - 瓦片 / 无线卡：`surfaceContainerHigh`（暗色为深灰块）。
 */
object ShizukuHomePalette {
    val heroInactiveBg = Color(0xFFF9DEDC)
    val heroInactiveFg = Color(0xFF410E0B)
    val heroActivatingBg = Color(0xFFD3E3FD)
    val heroActivatingFg = Color(0xFF041E49)
    val heroReadyBg = Color(0xFFFFFFFF)
    val heroReadyFg = Color(0xFF1A1B20)

    @Composable
    fun tileSurface(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun tileContent(): Color = MaterialTheme.colorScheme.onSurface
}
