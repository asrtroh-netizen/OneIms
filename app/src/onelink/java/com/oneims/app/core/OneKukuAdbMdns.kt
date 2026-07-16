package com.oneims.app.core

import android.content.Context

/**
 * OneLink 桩：无内嵌无线调试 mDNS。真实实现仅存在于 onekuku flavor。
 */
object OneKukuAdbMdns {
    data class Ports(
        val pairPort: Int?,
        val connectPort: Int?,
    )

    fun isWifiClientConnected(context: Context): Boolean = false

    fun waitForWifiClient(context: Context, timeoutMs: Long): Boolean = false

    suspend fun discover(
        context: Context,
        timeoutMs: Long = 0L,
    ): Ports = Ports(null, null)
}
