package com.oneims.app.core.privilege

import android.os.IBinder

/**
 * 优先 OneBridge；未就绪时回落 [fallback]（通常为 Shizuku）。
 *
 * 生命周期监听：对 primary / fallback **双源订阅**，这样 OneBridge-only
 * 或 Shizuku-only 都能驱动 Guard / UI 刷新。
 */
class FallbackPrivilegeBridge(
    private val primary: PrivilegeBridge,
    private val fallback: PrivilegeBridge,
) : PrivilegeBridge {
    private fun active(): PrivilegeBridge =
        if (primary.isRunning()) primary else fallback

    override fun isRunning(): Boolean = primary.isRunning() || fallback.isRunning()

    override fun isGranted(): Boolean = active().isGranted()

    override fun isReady(): Boolean = active().isReady()

    override fun requestPermission(requestCode: Int) {
        active().requestPermission(requestCode)
    }

    override fun getUid(): Int = active().getUid()

    override fun wrapSystemService(name: String): IBinder =
        active().wrapSystemService(name)

    override fun addBinderReceivedListener(listener: () -> Unit, sticky: Boolean) {
        primary.addBinderReceivedListener(listener, sticky = false)
        fallback.addBinderReceivedListener(listener, sticky = false)
        if (sticky && isRunning()) {
            listener()
        }
    }

    override fun removeBinderReceivedListener(listener: () -> Unit) {
        primary.removeBinderReceivedListener(listener)
        fallback.removeBinderReceivedListener(listener)
    }

    override fun addBinderDeadListener(listener: () -> Unit) {
        primary.addBinderDeadListener(listener)
        fallback.addBinderDeadListener(listener)
    }

    override fun removeBinderDeadListener(listener: () -> Unit) {
        primary.removeBinderDeadListener(listener)
        fallback.removeBinderDeadListener(listener)
    }

    override fun addRequestPermissionResultListener(
        listener: PrivilegeBridge.PermissionResultListener,
    ) {
        // 授权弹窗目前只存在于 Shizuku 回落路径；双源注册无害。
        primary.addRequestPermissionResultListener(listener)
        fallback.addRequestPermissionResultListener(listener)
    }

    override fun removeRequestPermissionResultListener(
        listener: PrivilegeBridge.PermissionResultListener,
    ) {
        primary.removeRequestPermissionResultListener(listener)
        fallback.removeRequestPermissionResultListener(listener)
    }
}
