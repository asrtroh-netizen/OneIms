package com.oneims.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.oneims.app.R

/**
 * 严格映射邻仓 Shizuku 本地资源（勿自造色值）。
 *
 * 真源：
 * - `.../values/colors.xml`（hero_* / app_color_*）
 * - `home_item_container.xml` → `?colorSurfaceContainerLow`
 * - `bg_action_tile.xml` → `?colorSurfaceContainerHigh`
 * - `themes.xml` → primary = app_color_light / app_color_dark（由 [Theme] 注入）
 */
object ShizukuHomePalette {
    @Composable
    fun heroInactiveBg(): Color = colorResource(R.color.shizuku_hero_inactive_bg)

    @Composable
    fun heroInactiveFg(): Color = colorResource(R.color.shizuku_hero_inactive_fg)

    @Composable
    fun heroActivatingBg(): Color = colorResource(R.color.shizuku_hero_activating_bg)

    @Composable
    fun heroActivatingFg(): Color = colorResource(R.color.shizuku_hero_activating_fg)

    @Composable
    fun heroReadyBg(): Color = colorResource(R.color.shizuku_hero_ready_bg)

    @Composable
    fun heroReadyFg(): Color = colorResource(R.color.shizuku_hero_ready_fg)

    /** 对应 themes 的 appColorPrimary（Theme.kt 已写入 colorScheme.primary）。 */
    @Composable
    fun accent(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun onAccent(): Color = MaterialTheme.colorScheme.onPrimary

    /** home_item_container */
    @Composable
    fun cardSurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    /** bg_action_tile */
    @Composable
    fun tileSurface(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun tileContent(): Color = MaterialTheme.colorScheme.onSurface
}
