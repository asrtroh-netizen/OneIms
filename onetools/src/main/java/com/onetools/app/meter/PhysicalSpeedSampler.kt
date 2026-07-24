package com.onetools.app.meter

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import java.util.concurrent.ConcurrentHashMap

/**
 * Samples RX/TX on physical transports only (Wi‑Fi / Cellular / Ethernet),
 * ignoring VPN to avoid double-counting.
 *
 * Approach adapted from Pixel Meter `SpeedDataSource` (Apache-2.0).
 * See `onetools/NOTICE`.
 */
class PhysicalSpeedSampler(
    private val connectivityManager: ConnectivityManager,
) {
    data class TrafficTotals(val rxBytes: Long, val txBytes: Long)

    private val validInterfaces = ConcurrentHashMap<Network, String>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            updateNetwork(network, caps, connectivityManager.getLinkProperties(network))
        }

        override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
            updateNetwork(network, connectivityManager.getNetworkCapabilities(network), props)
        }

        override fun onLost(network: Network) {
            validInterfaces.remove(network)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        validInterfaces.clear()
    }

    private fun updateNetwork(
        network: Network,
        caps: NetworkCapabilities?,
        props: LinkProperties?,
    ) {
        if (caps == null || props == null) {
            validInterfaces.remove(network)
            return
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            validInterfaces.remove(network)
            return
        }
        val physical = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!physical) {
            validInterfaces.remove(network)
            return
        }
        val iface = props.interfaceName
        if (!iface.isNullOrEmpty()) {
            validInterfaces[network] = iface
        }
    }

    fun readTotals(): TrafficTotals {
        var rx = 0L
        var tx = 0L
        for (iface in validInterfaces.values) {
            val r = TrafficStats.getRxBytes(iface)
            val t = TrafficStats.getTxBytes(iface)
            if (r != TrafficStats.UNSUPPORTED.toLong()) rx += r
            if (t != TrafficStats.UNSUPPORTED.toLong()) tx += t
        }
        return TrafficTotals(rx, tx)
    }
}

object SpeedFormat {
    fun formatRate(bytesPerSec: Long): String {
        if (bytesPerSec < 0) return "0 B/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.1f MB/s", mb)
            kb >= 1 -> String.format("%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }
}
