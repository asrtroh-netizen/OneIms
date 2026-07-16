package com.oneims.app.core.privilege

/**
 * OneKuku 产品线：内置 OneBridge 通道，首页品牌保持 OneKuku。
 */
object ChannelBridgeBootstrap {
    fun create(): PrivilegeBridge = OneBridgePrivilegeBridge()
}
