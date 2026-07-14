package com.oneims.app.core.privilege

import android.os.IBinder

/**
 * 优先 OneBridge；未就绪时回落 [fallback]（通常为 Shizuku）。
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
}
