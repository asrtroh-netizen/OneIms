package com.onetools.app.special.privilege

import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * OneTools 特色功能特权面：仅官方 Shizuku，MVP 服务名与 OneIMS PrivilegeBridge 对齐。
 */
object SpecialPrivilege {
    private val ALLOWED = setOf("activity", "carrier_config", "isub", "phone")

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun isGranted(): Boolean = try {
        isRunning() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun isReady(): Boolean = isRunning() && isGranted()

    fun getUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    fun wrapSystemService(name: String): IBinder {
        require(name in ALLOWED) { "SpecialPrivilege does not expose: $name" }
        return ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name))
    }
}
