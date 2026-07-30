package com.oneims.app.core.privilege

/**
 * 进程内特权桥单一真源。
 * 具体实现由各 productFlavor 的 [ChannelBridgeBootstrap] 注入：
 * OneKuku→OneBridge；OneLink→ShizukuPrivilegeBridge。
 */
object PrivilegeBridges {
    @Volatile
    var current: PrivilegeBridge = ChannelBridgeBootstrap.create()
}
