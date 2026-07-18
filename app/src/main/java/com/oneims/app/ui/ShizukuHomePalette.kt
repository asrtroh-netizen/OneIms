package com.oneims.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.oneims.app.R

/**
 * 首页色板：hero 粉/白来自邻仓资源；强调色跟全局 [Theme] primary（现已为白）。
 */
object ShizukuHomePalette {
    @Composable
    fun heroInactiveBg(): Color = colorResource(R.color.shizuku_hero_inactive_bg)

    @Composable
    fun heroInactiveFg(): Color = colorResource(R.color.shizuku_hero_inactive_fg)

    @Composable
    fun heroActivatingBg(): Color = colorResource(R.color.shizuku_hero_ready_bg)

    @Composable
    fun heroActivatingFg(): Color = colorResource(R.color.shizuku_hero_ready_fg)

    @Composable
    fun heroReadyBg(): Color = colorResource(R.color.shizuku_hero_ready_bg)

    @Composable
    fun heroReadyFg(): Color = colorResource(R.color.shizuku_hero_ready_fg)

    @Composable
    fun accent(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun onAccent(): Color = MaterialTheme.colorScheme.onPrimary

    @Composable
    fun cardSurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    @Composable
    fun tileSurface(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun tileContent(): Color = MaterialTheme.colorScheme.onSurface
}
