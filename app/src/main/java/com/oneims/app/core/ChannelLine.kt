package com.oneims.app.core

import com.oneims.app.BuildConfig
import com.oneims.app.R

/**
 * 双线产品身份：由 productFlavor 注入 [BuildConfig]，UI/激活路径只读本对象。
 *
 * - onelink：外置官方 Shizuku（Lite⊕Shizuku 协作模板）
 * - onekuku：同一协作分工的内循环 —— 增强旧 OneKuku 充当「通道侧」
 *   （docs/architecture/2026-07-30-onekuku-mirrors-lite-shizuku.md）
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
