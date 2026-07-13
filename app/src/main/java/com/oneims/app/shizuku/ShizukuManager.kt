package com.oneims.app.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 接入与权限管理。
 *
 * Shizuku 通过 ADB（或 root）启动一个特权进程，App 借它以 shell 身份调用系统隐藏 API，
 * 从而在免 root 前提下获得 MODIFY_PHONE_STATE 等敏感能力。
 */
object ShizukuManager {

    const val REQUEST_CODE = 4370

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun isGranted(): Boolean = try {
        isRunning() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (isRunning() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Throwable) {
            // 忽略：调用方会通过 isGranted() 复检
        }
    }
}
