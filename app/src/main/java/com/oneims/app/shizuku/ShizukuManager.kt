package com.oneims.app.shizuku

import com.oneims.app.core.privilege.PrivilegeBridge
import com.oneims.app.core.privilege.PrivilegeBridges

/**
 * 兼容入口：历史调用点仍可用，实际委托 [PrivilegeBridges]。
 * 新代码请直接用 [com.oneims.app.core.OneKukuManager] 或 [PrivilegeBridges.current]。
 */
object ShizukuManager {

    const val REQUEST_CODE: Int = PrivilegeBridge.DEFAULT_REQUEST_CODE

    fun isRunning(): Boolean = PrivilegeBridges.current.isRunning()

    fun isGranted(): Boolean = PrivilegeBridges.current.isGranted()

    fun requestPermission() {
        PrivilegeBridges.current.requestPermission(REQUEST_CODE)
    }
}
