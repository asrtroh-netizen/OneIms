package com.oneims.app.core

import com.oneims.app.BuildConfig
import com.oneims.app.R

/**
 * 双线产品身份：由 productFlavor 注入 [BuildConfig]，UI/激活路径只读本对象。
 *
 * - onekuku：内置 OneBridge，首页显示 OneKuku
 * - onelink：官方 Shizuku，首页显示 OneLink
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
