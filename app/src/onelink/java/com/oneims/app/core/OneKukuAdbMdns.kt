package com.oneims.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/**
 * OneLink：无内嵌无线调试 mDNS，但开机恢复仍需识别「旧网 STA 已连上」。
 * 探测逻辑与 onekuku 线对齐（ConnectivityManager + WifiManager 兜底）。
 */
object OneKukuAdbMdns {
    data class Ports(
        val pairPort: Int?,
        val connectPort: Int?,
    )

    @Suppress("DEPRECATION")
    fun isWifiClientConnected(context: Context): Boolean {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return true
                }
            }
        }
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        if (!wifi.isWifiEnabled) return false
        val info = wifi.connectionInfo ?: return false
        val state = info.supplicantState
        if (state == android.net.wifi.SupplicantState.COMPLETED ||
            state == android.net.wifi.SupplicantState.ASSOCIATED
        ) {
            return true
        }
        if (info.networkId >= 0) return true
        val ssid = info.ssid?.trim().orEmpty()
        return ssid.isNotEmpty() &&
            !ssid.equals("<unknown ssid>", ignoreCase = true) &&
            ssid != "\"\""
    }

    fun waitForWifiClient(context: Context, timeoutMs: Long): Boolean {
        val app = context.applicationContext
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isWifiClientConnected(app)) return true
            // 轮询收紧：开机恢复别在 1.5s 粒度上空转。
            Thread.sleep(500L)
        }
        return isWifiClientConnected(app)
    }

    suspend fun discover(
        context: Context,
        timeoutMs: Long = 0L,
    ): Ports = Ports(null, null)
}
