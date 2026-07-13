package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启广播接收器。
 *
 * 开机完成后，若用户开启了守护，则拉起 [GuardService]。真正的重应用由守护服务在
 * 「Shizuku binder 就绪」的瞬间触发（开机时 Shizuku 往往尚未启动，故不在此处直接写配置）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            if (ConfigStore.isGuardEnabled(context)) {
                GuardService.start(context)
            }
        }
    }
}
