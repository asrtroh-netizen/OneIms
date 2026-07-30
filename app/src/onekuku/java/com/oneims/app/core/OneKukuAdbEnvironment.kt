package com.oneims.app.core

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * 对齐 Shizuku V15 `EnvironmentUtils`：在 mDNS / `service.adb.tcp.port` 失效时，
 * 从 `/proc/net/tcp*` 挖出 adbd(uid=2000) 的 TLS 监听口。
 */
object OneKukuAdbEnvironment {

    private const val TAG = "OneIMS-AdbEnv"
    private const val ADBD_UID = 2000
    private const val TCP_LISTEN = "0A"
    private const val PREFS = "onekuku_adb_identity"
    private const val KEY_LAST_WIRELESS_PORT = "last_adb_wireless_port"
    private const val KEY_TCPIP_PORT = "tcpip_persist_port"

    /** 经典 tcpip 口；可用偏好覆盖（默认 5555）。 */
    fun persistTcpipPort(context: Context): Int {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_TCPIP_PORT, 5555)
        return raw.takeIf { it in 1..65535 } ?: 5555
    }

    fun setPersistTcpipPort(context: Context, port: Int) {
        if (port !in 1..65535) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TCPIP_PORT, port)
            .apply()
    }

    fun getLastAdbWirelessPort(context: Context): Int =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_WIRELESS_PORT, -1)

    fun setLastAdbWirelessPort(context: Context, port: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_WIRELESS_PORT, port.takeIf { it in 1..65535 } ?: -1)
            .apply()
    }

    fun clearLastAdbWirelessPort(context: Context) {
        setLastAdbWirelessPort(context, -1)
    }

    fun getAdbTcpPort(): Int {
        var port = systemPropertyInt("service.adb.tcp.port", -1)
        if (port == -1) port = systemPropertyInt("persist.adb.tcp.port", -1)
        return port
    }

    fun findAdbdListeningPorts(): List<Int> {
        val ports = linkedSetOf<Int>()
        for (path in arrayOf("/proc/net/tcp", "/proc/net/tcp6")) {
            val file = File(path)
            if (!file.canRead()) continue
            runCatching {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.readLine() // header
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.trim().split(Regex("\\s+"))
                        if (parts.size < 8) continue
                        if (!parts[3].equals(TCP_LISTEN, ignoreCase = true)) continue
                        val uid = parts[7].toIntOrNull() ?: continue
                        if (uid != ADBD_UID) continue
                        val local = parts[1]
                        val colon = local.lastIndexOf(':')
                        if (colon < 0 || colon == local.lastIndex) continue
                        val portHex = local.substring(colon + 1)
                        val port = portHex.toIntOrNull(16) ?: continue
                        if (port in 1..65535) ports.add(port)
                    }
                }
            }.onFailure {
                Log.w(TAG, "read $path failed: ${it.message}")
            }
        }
        return ports.toList()
    }

    fun findAdbdWirelessPort(): Int {
        val ports = findAdbdListeningPorts()
        if (ports.isEmpty()) return -1
        val classic = getAdbTcpPort()
        return ports.firstOrNull { it != classic && it != 5555 } ?: ports.first()
    }

    fun isLocalPortInUse(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            ServerSocket().use {
                it.bind(InetSocketAddress("127.0.0.1", port), 1)
                false
            }
        } catch (_: IOException) {
            true
        }
    }

    /**
     * 解析当前可尝试的 connect 口：系统属性 → 配置 tcpip → /proc → 上次成功且仍监听。
     * 对齐 V15 `AdbWirelessHelper.getStartableAdbPort`。
     */
    fun resolveStartableConnectPort(context: Context): Int? {
        val systemPort = getAdbTcpPort()
        if (systemPort in 1..65535) return systemPort

        val configured = persistTcpipPort(context)
        if (isLocalPortInUse(configured)) return configured

        val wireless = findAdbdWirelessPort()
        if (wireless in 1..65535) return wireless

        val last = getLastAdbWirelessPort(context)
        if (last in 1..65535 && isLocalPortInUse(last)) return last
        if (last in 1..65535) {
            Log.i(TAG, "stale last wireless port=$last cleared")
            clearLastAdbWirelessPort(context)
        }
        return null
    }

    /**
     * 在 [timeoutMs] 内以 [pollMs] 轮询 /proc 与 last-port，供 mDNS 空窗期抢端口。
     * 对齐 V15 SelfStarterService 400ms poll。
     */
    fun pollStartableConnectPort(
        context: Context,
        timeoutMs: Long = 12_000L,
        pollMs: Long = 400L,
    ): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            resolveStartableConnectPort(context)?.let { return it }
            Thread.sleep(pollMs)
        }
        return resolveStartableConnectPort(context)
    }

    private fun systemPropertyInt(key: String, default: Int): Int {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            method.invoke(null, key, default) as Int
        }.getOrDefault(default)
    }
}
