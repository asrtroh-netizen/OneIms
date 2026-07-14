package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机 / 解锁 / SIM 状态变化 → 调度 [OneKukuBootRestoreService]。
 * 不在此处直接写配置。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            -> {
                // 掉线守护开启，或存在上次成功配置：开机拉起守护，等待 OneKuku 就绪后重应用临时覆盖。
                if (ConfigStore.isGuardEnabled(context) ||
                    ConfigStore.lastApplied(context) != null
                ) {
                    GuardService.start(context)
                }
                if (ConfigStore.isOneKukuBootAutoCheck(context)) {
                    val debounce = if (action == Intent.ACTION_USER_UNLOCKED) 1_000L else 3_000L
                    OneKukuBootRestoreService.enqueue(context, debounceMs = debounce)
                }
            }
            "android.intent.action.SIM_STATE_CHANGED" -> {
                if (ConfigStore.isOneKukuBootAutoCheck(context)) {
                    OneKukuBootRestoreService.enqueue(context, debounceMs = 5_000L)
                }
            }
        }
    }
}
