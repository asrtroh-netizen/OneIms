package com.oneims.app.core.privilege

/**
 * OneKuku 产品线：内循环通道注入点（固定 OneBridge）。
 */
object ChannelBridgeBootstrap {
    fun create(): PrivilegeBridge = OneBridgePrivilegeBridge()
}
