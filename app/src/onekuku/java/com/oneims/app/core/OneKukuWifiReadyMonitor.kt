package com.oneims.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.oneims.app.onekuku.OneKukuBootRestoreStore
import com.oneims.app.onekuku.OneKukuBootUiHint

/**
 * 对齐 V15 `WifiReadyMonitor`：Wi‑Fi 就绪后自动续跑开机恢复，无需用户点「重试」。
 */
object OneKukuWifiReadyMonitor {
    private const val TAG = "OneIMS-WifiReady"
    private const val DEBOUNCE_MS = 1_500L

    @Volatile
    private var registered = false

    @Volatile
    private var lastEnqueueAtMs = 0L

    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun ensureRegistered(context: Context) {
        if (registered) return
        if (ChannelLine.usesShizuku) return
        if (!ConfigStore.isOneKukuBootAutoCheck(context)) return

        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                maybeRetry(app)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) {
                    maybeRetry(app)
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, cb)
            callback = cb
            registered = true
            Log.i(TAG, "registered")
            maybeRetry(app)
        } catch (e: Exception) {
            Log.w(TAG, "register failed", e)
        }
    }

    @Synchronized
    fun unregister(context: Context) {
        if (!registered) return
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val cb = callback
        if (cm != null && cb != null) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        callback = null
        registered = false
    }

    private fun maybeRetry(context: Context) {
        if (!ConfigStore.isOneKukuBootAutoCheck(context)) return
        if (OneKukuManager.isRunning()) return

        val hint = OneKukuBootRestoreStore.readHint(context)
        val waitingWifi = hint == OneKukuBootUiHint.WAITING_WIFI
        val notAttempted = !OneKukuBootRestoreStore.hasAttemptedThisBoot(context)
        if (!waitingWifi && !notAttempted) return
        if (!OneKukuAdbMdns.isWifiClientConnected(context)) return

        val now = System.currentTimeMillis()
        if (now - lastEnqueueAtMs < DEBOUNCE_MS) return
        lastEnqueueAtMs = now

        Log.i(TAG, "Wi‑Fi up → enqueue boot restore")
        runCatching {
            OneKukuBootRestoreService.enqueue(context, debounceMs = 500L)
        }.onFailure {
            Log.w(TAG, "enqueue failed", it)
        }
    }
}
