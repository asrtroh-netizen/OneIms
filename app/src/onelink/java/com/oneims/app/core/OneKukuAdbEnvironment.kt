package com.oneims.app.core

import android.content.Context

/**
 * OneLink 桩：无线调试端口探测仅 OneKuku 内循环需要。
 * 放在 onelink 源集，避免 main 里的 BootRestore 引用在 Lite 编译失败。
 */
object OneKukuAdbEnvironment {
    fun persistTcpipPort(context: Context): Int = 5555

    fun setPersistTcpipPort(context: Context, port: Int) = Unit

    fun getLastAdbWirelessPort(context: Context): Int = -1

    fun setLastAdbWirelessPort(context: Context, port: Int) = Unit

    fun clearLastAdbWirelessPort(context: Context) = Unit

    fun getAdbTcpPort(): Int = -1

    fun findAdbdListeningPorts(): List<Int> = emptyList()

    fun isLocalPortInUse(port: Int): Boolean = false

    fun resolveStartableConnectPort(context: Context): Int? = null

    fun pollStartableConnectPort(
        context: Context,
        timeoutMs: Long = 12_000L,
        pollMs: Long = 400L,
    ): Int? = null
}
