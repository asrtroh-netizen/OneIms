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
 * 开机 / 解锁 / SIM / Wi‑Fi 状态变化 → 调度 [OneKukuBootRestoreService]。
 * 不在此处直接写配置。
 *
 * 使用 [goAsync] 拉长广播生命周期，避免进程在前台服务尚未 startForeground 前被回收。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            -> {
                val pending = goAsync()
                try {
                    // 掉线守护开启，或存在上次成功配置：开机拉起守护，等待 OneKuku 就绪后重应用临时覆盖。
                    if (ConfigStore.isGuardEnabled(context) ||
                        ConfigStore.lastApplied(context) != null
                    ) {
                        GuardService.start(context)
                    }
                    if (ConfigStore.isOneKukuBootAutoCheck(context)) {
                        val debounce = if (action == Intent.ACTION_USER_UNLOCKED) 500L else 1_000L
                        Log.i(TAG, "boot action=$action enqueue restore debounce=$debounce")
                        OneKukuBootRestoreService.enqueue(context, debounceMs = debounce)
                    }
                } finally {
                    pending.finish()
                }
            }
            "android.intent.action.SIM_STATE_CHANGED" -> {
                if (ConfigStore.isOneKukuBootAutoCheck(context)) {
                    OneKukuBootRestoreService.enqueue(context, debounceMs = 5_000L)
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                // 已配对开机等 Wi‑Fi：系统连上 STA 后再跑静默激活/恢复。
                if (!ConfigStore.isOneKukuBootAutoCheck(context)) return
                if (OneKukuBootRestoreStore.readHint(context) != OneKukuBootUiHint.WAITING_WIFI &&
                    OneKukuBootRestoreStore.hasAttemptedThisBoot(context)
                ) {
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
                Log.i(TAG, "wifi connected → enqueue restore")
                OneKukuBootRestoreService.enqueue(context, debounceMs = 1_000L)
            }
        }
    }

    companion object {
        private const val TAG = "OneIMS-OneKuku"
    }
}
