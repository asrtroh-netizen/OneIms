package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.oneims.app.onekuku.OneKukuBootRestoreStore
import com.oneims.app.onekuku.OneKukuBootUiHint

/**
 * 开机 / 解锁 / Wi‑Fi 状态变化 → 调度 [OneKukuBootRestoreService]。
 * 不在此处直接写配置。
 *
 * 使用 [goAsync] 拉长广播生命周期，避免进程在前台服务尚未 startForeground 前被回收。
 *
 * FGS 纪律（Android 12+ / targetSdk 36）：
 * - 只有 [Intent.ACTION_BOOT_COMPLETED] 带临时白名单，可 startForegroundService
 * - LOCKED_BOOT / USER_UNLOCKED / 普通 Wi‑Fi·SIM 广播上抢启会被 Disallowed
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                val pending = goAsync()
                try {
                    // Direct Boot：只拉守护；恢复编排依赖 CE 存储与无线调试，等解锁后再跑。
                    if (ConfigStore.isGuardEnabled(context) ||
                        ConfigStore.lastApplied(context) != null
                    ) {
                        GuardService.start(context)
                    }
                    Log.i(TAG, "boot action=$action skip restore enqueue until unlocked")
                } finally {
                    pending.finish()
                }
            }
            Intent.ACTION_USER_UNLOCKED -> {
                val pending = goAsync()
                try {
                    if (ConfigStore.isGuardEnabled(context) ||
                        ConfigStore.lastApplied(context) != null
                    ) {
                        GuardService.start(context)
                    }
                    // 无 BOOT_COMPLETED 白名单；现代系统解锁后会投递 BOOT_COMPLETED。
                    Log.i(TAG, "boot action=$action skip restore enqueue (wait BOOT_COMPLETED allowlist)")
                } finally {
                    pending.finish()
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                try {
                    if (ConfigStore.isGuardEnabled(context) ||
                        ConfigStore.lastApplied(context) != null
                    ) {
                        GuardService.start(context)
                    }
                    if (ConfigStore.isOneKukuBootAutoCheck(context)) {
                        Log.i(TAG, "boot action=$action enqueue restore debounce=1000")
                        OneKukuBootRestoreService.enqueue(context, debounceMs = 1_000L)
                    }
                } finally {
                    pending.finish()
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                // 仅「本开机已停在等 Wi‑Fi」时续跑；禁止在未 attempted 时靠 Wi‑Fi 广播抢启 FGS。
                if (!ConfigStore.isOneKukuBootAutoCheck(context)) return
                if (OneKukuBootRestoreStore.readHint(context) != OneKukuBootUiHint.WAITING_WIFI) {
                    return
                }
                @Suppress("DEPRECATION")
                val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                } else {
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                }
                if (info?.isConnected != true) return
                if (!OneKukuAdbMdns.isWifiClientConnected(context)) return
                Log.i(TAG, "wifi connected while WAITING_WIFI → enqueue restore")
                OneKukuBootRestoreService.enqueue(context, debounceMs = 1_000L)
            }
            // SIM 稳定等待改由 OneKukuBootRestoreCoordinator 内完成，避免开机期无白名单 FGS。
        }
    }

    companion object {
        private const val TAG = "OneIMS-OneKuku"
    }
}
