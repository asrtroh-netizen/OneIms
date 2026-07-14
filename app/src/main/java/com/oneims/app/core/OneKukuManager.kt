package com.oneims.app.core

import com.oneims.app.core.privilege.PrivilegeBridge
import com.oneims.app.core.privilege.PrivilegeBridges

/**
 * 对外特权通道门面：业务与 UI 只认 OneKuku，不再暴露第三方通道名。
 * 实现委托 [PrivilegeBridges]；Root 通道指「以 UID 0 启动的同一通道」，非独立 su。
 */
object OneKukuManager {
    const val REQUEST_CODE: Int = PrivilegeBridge.DEFAULT_REQUEST_CODE

    fun isRunning(): Boolean = PrivilegeBridges.current.isRunning()

    fun isGranted(): Boolean = PrivilegeBridges.current.isGranted()

    /** 可执行特权写入的就绪态。 */
    fun isReady(): Boolean = PrivilegeBridges.current.isReady()

    fun requestActivation() {
        PrivilegeBridges.current.requestPermission(REQUEST_CODE)
    }

    /** Root 直调是否可用（特权进程以 UID 0 运行时）。 */
    fun isRootChannel(): Boolean = PrivilegeBridges.current.getUid() == 0
}
