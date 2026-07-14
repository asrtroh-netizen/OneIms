package com.oneims.app.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 发现本机无线调试的配对/连接端口（`_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp`）。
 */
object OneKukuAdbMdns {

    data class Ports(
        val pairPort: Int?,
        val connectPort: Int?,
    )

    private const val TYPE_PAIRING = "_adb-tls-pairing._tcp"
    private const val TYPE_CONNECT = "_adb-tls-connect._tcp"
    private const val DISCOVER_TIMEOUT_MS = 8_000L

    suspend fun discover(context: Context): Ports {
        val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return Ports(null, null)
        val pair = withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { discoverOne(nsd, TYPE_PAIRING) }
        val connect = withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { discoverOne(nsd, TYPE_CONNECT) }
        return Ports(pairPort = pair, connectPort = connect)
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
                complete(null)
            }
        }
}
