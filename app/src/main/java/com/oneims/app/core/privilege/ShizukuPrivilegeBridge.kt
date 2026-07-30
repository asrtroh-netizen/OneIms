package com.oneims.app.core.privilege

import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * 官方 Shizuku API 实现的 [PrivilegeBridge]。
 *
 * - onelink：外置 Shizuku（默认路径）
 * - onekuku + [ChannelEngine.CARE_MIN]：宿主内嵌 MINI server 的客户端面（P3a；
 *   server 类进 APK 属后续里程碑，本类可先编译）
 */
class ShizukuPrivilegeBridge : PrivilegeBridge {
    private val receivedAdapters =
        ConcurrentHashMap<() -> Unit, Shizuku.OnBinderReceivedListener>()
    private val deadAdapters =
        ConcurrentHashMap<() -> Unit, Shizuku.OnBinderDeadListener>()
    private val permissionAdapters =
        ConcurrentHashMap<
            PrivilegeBridge.PermissionResultListener,
            Shizuku.OnRequestPermissionResultListener,
            >()

    override fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    override fun isGranted(): Boolean = try {
        isRunning() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    override fun requestPermission(requestCode: Int) {
        try {
            if (isRunning() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {
            // 调用方通过 isGranted() 复检
        }
    }

    override fun getUid(): Int =
        runCatching { Shizuku.getUid() }.getOrDefault(-1)

    override fun wrapSystemService(name: String): IBinder {
        require(name in PrivilegeBridge.MVP_SYSTEM_SERVICES) {
            "PrivilegeBridge MVP does not expose system service: $name"
        }
        return ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name))
    }

    override fun addBinderReceivedListener(listener: () -> Unit, sticky: Boolean) {
        val adapter = Shizuku.OnBinderReceivedListener { listener() }
        receivedAdapters[listener] = adapter
        if (sticky) {
            runCatching { Shizuku.addBinderReceivedListenerSticky(adapter) }
        } else {
            runCatching { Shizuku.addBinderReceivedListener(adapter) }
        }
    }

    override fun removeBinderReceivedListener(listener: () -> Unit) {
        val adapter = receivedAdapters.remove(listener) ?: return
        runCatching { Shizuku.removeBinderReceivedListener(adapter) }
    }

    override fun addBinderDeadListener(listener: () -> Unit) {
        val adapter = Shizuku.OnBinderDeadListener { listener() }
        deadAdapters[listener] = adapter
        runCatching { Shizuku.addBinderDeadListener(adapter) }
    }

    override fun removeBinderDeadListener(listener: () -> Unit) {
        val adapter = deadAdapters.remove(listener) ?: return
        runCatching { Shizuku.removeBinderDeadListener(adapter) }
    }

    override fun addRequestPermissionResultListener(
        listener: PrivilegeBridge.PermissionResultListener,
    ) {
        val adapter = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            listener.onRequestPermissionResult(requestCode, grantResult)
        }
        permissionAdapters[listener] = adapter
        runCatching { Shizuku.addRequestPermissionResultListener(adapter) }
    }

    override fun removeRequestPermissionResultListener(
        listener: PrivilegeBridge.PermissionResultListener,
    ) {
        val adapter = permissionAdapters.remove(listener) ?: return
        runCatching { Shizuku.removeRequestPermissionResultListener(adapter) }
    }
}
