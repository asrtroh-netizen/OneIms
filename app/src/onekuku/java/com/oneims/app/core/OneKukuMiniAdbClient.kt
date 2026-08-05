package com.oneims.app.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OneKuku 专用轻量无线调试客户端（Mini ADB）。
 *
 * 只暴露：transport 探测 / pair / connect / 白名单 shell。
 * 不提供通用 adb UI、终端、任意命令、install/push/pull/logcat。
 * 底层协议实现委托 [OneKukuEmbeddedAdbActivator]（libadb-android TLS，非完整 adb binary）。
 */
object OneKukuMiniAdbClient {

    private const val TAG = "OneKuku-MiniAdb"
    private const val HOST = "127.0.0.1"

    data class PairingInput(
        val code: String,
        val pairPortOverride: Int? = null,
    )

    sealed class Outcome {
        data object NeedPairingCode : Outcome()
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    /** 解析通知栏输入：`123456` / `37123 123456` / `37123:123456` */
    fun parsePairingInput(raw: String): PairingInput? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val spaced = Regex("""^(\d{2,5})\s+(\d{6})$""").matchEntire(text)
        if (spaced != null) {
            return PairingInput(
                code = spaced.groupValues[2],
                pairPortOverride = spaced.groupValues[1].toIntOrNull(),
            )
        }
        val colon = Regex("""^(\d{2,5}):(\d{6})$""").matchEntire(text)
        if (colon != null) {
            return PairingInput(
                code = colon.groupValues[2],
                pairPortOverride = colon.groupValues[1].toIntOrNull(),
            )
        }
        val digits = text.filter { it.isDigit() }
        if (digits.length == 6) return PairingInput(code = digits)
        if (digits.length > 6) {
            val code = digits.takeLast(6)
            val port = digits.dropLast(6).toIntOrNull()
            if (port != null && port in 1..65535) {
                return PairingInput(code = code, pairPortOverride = port)
            }
        }
        return null
    }

    /** 是否已发现可用 connect 端口（或可无需再配对直接拉起）。 */
    suspend fun checkTransport(context: Context): Boolean = withContext(Dispatchers.IO) {
        val ports = OneKukuAdbMdns.discover(context.applicationContext)
        Log.i(TAG, "transport pair=${ports.pairPort} connect=${ports.connectPort}")
        ports.connectPort != null
    }

    /**
     * 已有 transport：直接 connect + 白名单启动命令。
     * 无 transport：返回 [Outcome.NeedPairingCode]。
     */
    suspend fun activateExistingOrNeedPair(
        context: Context,
        forceRestart: Boolean = false,
    ): Outcome =
        withContext(Dispatchers.IO) {
            when (
                val o = OneKukuEmbeddedAdbActivator.activate(
                    context,
                    pairingCode = null,
                    forceRestart = forceRestart,
                )
            ) {
                is OneKukuEmbeddedAdbActivator.Outcome.NeedPairingCode -> Outcome.NeedPairingCode
                is OneKukuEmbeddedAdbActivator.Outcome.Success -> Outcome.Success(o.detail)
                is OneKukuEmbeddedAdbActivator.Outcome.Failed -> Outcome.Failed(o.reason)
            }
        }

    /** 用户从通知栏提交配对码后：pair → connect → 白名单启动。 */
    suspend fun pairConnectAndStart(context: Context, rawInput: String): Outcome =
        withContext(Dispatchers.IO) {
            val parsed = parsePairingInput(rawInput)
                ?: return@withContext Outcome.Failed("invalid_pairing_input")
            if (parsed.pairPortOverride != null) {
                Log.i(TAG, "pairing port override=${parsed.pairPortOverride}")
            }
            when (
                val o = OneKukuEmbeddedAdbActivator.activate(
                    context,
                    pairingCode = parsed.code,
                    pairPortOverride = parsed.pairPortOverride,
                )
            ) {
                is OneKukuEmbeddedAdbActivator.Outcome.NeedPairingCode -> Outcome.NeedPairingCode
                is OneKukuEmbeddedAdbActivator.Outcome.Success -> Outcome.Success(o.detail)
                is OneKukuEmbeddedAdbActivator.Outcome.Failed -> Outcome.Failed(o.reason)
            }
        }

    /** 仅允许 OneBridge 启动串，以及临时 Root 实验用的固定命令。 */
    fun isWhitelistedShell(command: String): Boolean {
        val c = command.trim()
        if (c.isEmpty()) return false
        if (c.contains("OneBridge_started") || c.contains("app_process")) {
            return c.contains("onebridge_server") || c.contains("BridgeService")
        }
        // 临时 Root 实验：只放行 comet preload 相关固定形态，禁止任意用户串。
        if (c == TEMP_ROOT_PROBE_SO) return true
        if (c == TEMP_ROOT_VERIFY_SU_TMP || c == TEMP_ROOT_VERIFY_SU_APEX) return true
        if (c.startsWith("LD_PRELOAD=") &&
            c.contains("/data/local/tmp/preload-comet.so") &&
            c.endsWith("/system/bin/id")
        ) {
            return true
        }
        if (c.startsWith("cp ") &&
            c.contains("preload-comet.so") &&
            c.contains("/data/local/tmp/preload-comet.so")
        ) {
            return true
        }
        return false
    }

    const val TEMP_ROOT_PROBE_SO: String =
        "test -f /data/local/tmp/preload-comet.so && echo HAS_SO || echo NO_SO"
    const val TEMP_ROOT_VERIFY_SU_TMP: String = "/data/local/tmp/su -c id"
    const val TEMP_ROOT_VERIFY_SU_APEX: String = "/apex/com.android.virt/bin/su -c id"
    const val TEMP_ROOT_LD_PRELOAD: String =
        "LD_PRELOAD=/data/local/tmp/preload-comet.so /system/bin/id"

    @Suppress("unused")
    fun hostLoopback(): String = HOST
}
