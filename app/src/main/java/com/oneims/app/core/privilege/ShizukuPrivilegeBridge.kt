package com.oneims.app.core.privilege

import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 过渡实现：用官方 Shizuku 满足 [PrivilegeBridge]。
 * Phase 1 自研桥验收后可替换为 OneBridge 实现，调用方无需改动。
 */
class ShizukuPrivilegeBridge : PrivilegeBridge {
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
}
