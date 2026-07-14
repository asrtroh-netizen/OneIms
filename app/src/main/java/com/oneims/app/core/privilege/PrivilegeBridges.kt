package com.oneims.app.core.privilege

/**
 * 进程内特权桥单一真源。默认 Shizuku 回落；OneBridge 就绪后切换 [current]。
 */
object PrivilegeBridges {
    @Volatile
    var current: PrivilegeBridge = ShizukuPrivilegeBridge()
}
