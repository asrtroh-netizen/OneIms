package com.oneims.app.core.privilege

/**
 * 进程内特权桥单一真源。Phase3 起仅挂 OneBridge，不再回落 Shizuku。
 */
object PrivilegeBridges {
    @Volatile
    var current: PrivilegeBridge = OneBridgePrivilegeBridge()
}
