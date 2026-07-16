package com.oneims.app.core.privilege

/**
 * OneLink 产品线：特权通道借用官方 Shizuku，首页品牌显示 OneLink。
 */
object ChannelBridgeBootstrap {
    fun create(): PrivilegeBridge = ShizukuPrivilegeBridge()
}
