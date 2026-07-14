package com.oneims.app.core.privilege

/**
 * 进程内特权桥单一真源。优先 OneBridge，回落 Shizuku。
 */
object PrivilegeBridges {
    @Volatile
    var current: PrivilegeBridge = FallbackPrivilegeBridge(
        primary = OneBridgePrivilegeBridge(),
        fallback = ShizukuPrivilegeBridge(),
    )
}
