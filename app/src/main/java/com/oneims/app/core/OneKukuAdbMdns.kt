package com.oneims.app.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 发现本机无线调试的配对/连接端口（`_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp`）。
 *
 * 热点/本机回环场景下必须持有 MulticastLock，否则 NSD 经常扫不到端口。
 */
object OneKukuAdbMdns {

    data class Ports(
        val pairPort: Int?,
        val connectPort: Int?,
    )

    private const val TAG = "OneIMS-AdbMdns"
    private const val TYPE_PAIRING = "_adb-tls-pairing._tcp"
    private const val TYPE_CONNECT = "_adb-tls-connect._tcp"
    private const val DISCOVER_TIMEOUT_MS = 6_000L

    /** 手机是否作为 Wi‑Fi 客户端已关联 AP（不含仅开 SoftAP/个人热点）。 */
    @Suppress("DEPRECATION")
    fun isWifiClientConnected(context: Context): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        if (!wifi.isWifiEnabled) return false
        val info = wifi.connectionInfo ?: return false
        if (info.networkId < 0) return false
        val ssid = info.ssid?.trim().orEmpty()
        return ssid.isNotEmpty() &&
            !ssid.equals("<unknown ssid>", ignoreCase = true) &&
            ssid != "\"\""
    }

    suspend fun discover(context: Context): Ports {
        val app = context.applicationContext
        val nsd = app.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return Ports(null, null)
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("onekuku-adb-mdns")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        return try {
            coroutineScope {
                val pairDeferred = async {
                    withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { discoverOne(nsd, TYPE_PAIRING) }
                }
                val connectDeferred = async {
                    withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { discoverOne(nsd, TYPE_CONNECT) }
                }
                Ports(pairPort = pairDeferred.await(), connectPort = connectDeferred.await())
            }
        } finally {
            runCatching {
                if (lock?.isHeld == true) lock.release()
            }.onFailure { Log.w(TAG, "release multicast lock", it) }
        }
    }

    private suspend fun discoverOne(nsd: NsdManager, serviceType: String): Int? =
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            lateinit var discovery: NsdManager.DiscoveryListener

            fun complete(port: Int?) {
                if (!finished.compareAndSet(false, true)) return
                runCatching { nsd.stopServiceDiscovery(discovery) }
                if (cont.isActive) cont.resume(port)
            }

            discovery = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(TAG, "startDiscovery failed type=$serviceType code=$errorCode")
                    complete(null)
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

                override fun onDiscoveryStarted(serviceType: String?) = Unit

                override fun onDiscoveryStopped(serviceType: String?) = Unit

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo == null) return
                    nsd.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo?,
                                errorCode: Int,
                            ) {
                                // 继续等其它实例
                            }

                            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                                val port = resolved?.port ?: return
                                if (port > 0) complete(port)
                            }
                        },
                    )
                }
            }

            cont.invokeOnCancellation {
                runCatching { nsd.stopServiceDiscovery(discovery) }
            }
            runCatching {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery)
            }.onFailure {
                Log.w(TAG, "discoverServices failed type=$serviceType", it)
                complete(null)
            }
        }
}
