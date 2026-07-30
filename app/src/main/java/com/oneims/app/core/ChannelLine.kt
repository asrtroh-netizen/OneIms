package com.oneims.app.core

import com.oneims.app.BuildConfig
import com.oneims.app.R

/**
 * 双线产品身份：由 productFlavor 注入 [BuildConfig]，UI/激活路径只读本对象。
 *
 * 发行形态（逻辑同构，差异仅独立/分割）：
 * - onelink：**分割** — 外置官方 Shizuku + Lite 业务 App
 * - onekuku：**独立** — 同一协作分工的内循环，增强旧 OneKuku 充当「通道侧」
 *
 * 见 docs/architecture/2026-07-30-onekuku-mirrors-lite-shizuku.md §0
 */
object ChannelLine {
    const val ONEKUKU: String = "onekuku"
    const val ONELINK: String = "onelink"

    val id: String get() = BuildConfig.CHANNEL_LINE

    val usesEmbeddedBridge: Boolean get() = BuildConfig.CHANNEL_USES_EMBEDDED_BRIDGE

    val usesShizuku: Boolean get() = !usesEmbeddedBridge

    val isOneLink: Boolean get() = id == ONELINK

    val displayNameRes: Int = R.string.channel_display_name
}
