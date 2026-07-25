package com.onebattery.app.channel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * OneTools 通道：仅走官方 Shizuku（对齐 OneLink，不引入 `:bridge`）。
 */
object ShizukuChannel {
    const val REQUEST_CODE = 1001

    private val SHIZUKU_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager",
    )

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

    fun isServiceReady(): Boolean = isRunning() && isGranted()

    fun requestPermission() {
        try {
            if (isRunning() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (_: Throwable) {
            // caller re-checks isGranted()
        }
    }

    fun isShizukuInstalled(context: Context): Boolean =
        SHIZUKU_PACKAGES.any { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

    /** 打开已安装的 Shizuku 管理端；失败返回 false。 */
    fun openShizukuManager(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in SHIZUKU_PACKAGES) {
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(launch)
                true
            }.getOrDefault(false)
        }
        return false
    }

    fun addBinderReceivedListener(listener: () -> Unit) {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(
                Shizuku.OnBinderReceivedListener { listener() },
            )
        }
    }

    fun addBinderDeadListener(listener: () -> Unit) {
        runCatching {
            Shizuku.addBinderDeadListener(
                Shizuku.OnBinderDeadListener { listener() },
            )
        }
    }

    fun addPermissionResultListener(listener: (Int, Int) -> Unit) {
        runCatching {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                listener(requestCode, grantResult)
            }
        }
    }
}
