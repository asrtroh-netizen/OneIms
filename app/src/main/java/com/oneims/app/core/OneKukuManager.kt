package com.oneims.app.core

import com.oneims.app.shizuku.ShizukuManager
import rikka.shizuku.Shizuku

/**
 * 对外特权通道门面：业务与 UI 只认 OneKuku，不再暴露第三方通道名。
 * 当前实现仍委托既有特权进程；Root 通道指「以 UID 0 启动的同一通道」，非独立 su。
 */
object OneKukuManager {
    const val REQUEST_CODE: Int = ShizukuManager.REQUEST_CODE

    fun isRunning(): Boolean = ShizukuManager.isRunning()

    fun isGranted(): Boolean = ShizukuManager.isGranted()

    /** 可执行特权写入的就绪态。 */
    fun isReady(): Boolean = isRunning() && isGranted()

    fun requestActivation() {
        ShizukuManager.requestPermission()
    }

    /** Root 直调是否可用（特权进程以 UID 0 运行时）。 */
    fun isRootChannel(): Boolean =
        runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
}
