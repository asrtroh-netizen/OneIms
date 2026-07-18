package com.oneims.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.oneims.app.R

/**
 * 首页色板：hero 未激活仍用邻仓粉卡；**蓝色强调全部改为白**（启动钮 / 激活中卡）。
 * 卡面 / 瓦片仍跟 Shizuku 的 surfaceContainerLow / High。
 */
object ShizukuHomePalette {
    @Composable
    fun heroInactiveBg(): Color = colorResource(R.color.shizuku_hero_inactive_bg)

    @Composable
    fun heroInactiveFg(): Color = colorResource(R.color.shizuku_hero_inactive_fg)

    /** 原 hero_activating 蓝卡 → 按需求改为白卡。 */
    @Composable
    fun heroActivatingBg(): Color = colorResource(R.color.shizuku_hero_ready_bg)

    @Composable
    fun heroActivatingFg(): Color = colorResource(R.color.shizuku_hero_ready_fg)

    @Composable
    fun heroReadyBg(): Color = colorResource(R.color.shizuku_hero_ready_bg)

    @Composable
    fun heroReadyFg(): Color = colorResource(R.color.shizuku_hero_ready_fg)

    /** 原蓝「启动」→ 白底深字。 */
    @Composable
    fun accent(): Color = Color.White

    @Composable
    fun onAccent(): Color = colorResource(R.color.shizuku_hero_ready_fg)

    @Composable
    fun cardSurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    @Composable
    fun tileSurface(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun tileContent(): Color = MaterialTheme.colorScheme.onSurface
}
