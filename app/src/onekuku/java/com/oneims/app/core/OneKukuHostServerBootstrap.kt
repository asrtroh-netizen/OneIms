package com.oneims.app.core

import android.content.Context
import android.util.Log
import com.oneims.app.core.privilege.ChannelEngine
import com.oneims.caremin.CareMinBootShell
import com.oneims.caremin.CareMinHostConstants
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * CARE_MIN 冷启 / 保活：保证宿主内嵌 [CareMinHostConstants.PROCESS_NICE_NAME] 进程在跑。
 *
 * 外置 V15 binder 让 [OneKukuManager.isReady] 变 true **不够**——用户要的是 `onekuku_server` 本身。
 * 拉起顺序：Root su → 已有 Shizuku.newProcess 代执行 → 内嵌无线 ADB。
 */
object OneKukuHostServerBootstrap {
    private const val TAG = "OneIMS-HostServerBoot"
    private const val ALIVE_WAIT_MS = 10_000L
    private const val ALIVE_POLL_MS = 400L
    /** 重启后 adb_wifi=1 但 tcpip/TLS 口常晚数秒～数十秒才监听。 */
    private const val PAIRED_BOOT_PORT_WAIT_MS = 45_000L

    fun ensureRunning(context: Context): Boolean {
        if (ChannelEngine.current() != ChannelEngine.CARE_MIN) {
            return true
        }
        if (isHostServerAlive()) {
            Log.i(TAG, "host server already alive")
            return true
        }
        val cmd = CareMinBootShell.command(
            packageName = context.packageName,
            forceRestart = false,
        )
        Log.i(TAG, "ensureRunning: try Root → Shizuku → wireless ADB")

        if (tryRoot(cmd) && waitUntilAlive()) {
            Log.i(TAG, "host server up via Root")
            return true
        }
        if (tryShizukuShell(cmd) && waitUntilAlive()) {
            Log.i(TAG, "host server up via existing Shizuku")
            return true
        }
        if (tryWirelessAdb(context) && waitUntilAlive()) {
            Log.i(TAG, "host server up via wireless ADB")
            return true
        }
        val alive = isHostServerAlive()
        Log.w(TAG, "ensureRunning failed alive=$alive")
        return alive
    }

    fun isHostServerAlive(): Boolean {
        // binder 已通 ≈ 宿主 onekuku_server 在跑（冷启无 su/pidof 时也要认）。
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return true
        }
        val nice = CareMinHostConstants.PROCESS_NICE_NAME
        val viaShizuku = execShizukuCapture(arrayOf("pidof", nice))
            ?.trim()
            ?.isNotEmpty() == true
        if (viaShizuku) return true
        val viaSu = execSuCapture("pidof $nice")
            ?.trim()
            ?.isNotEmpty() == true
        if (viaSu) return true
        // 无探测通道时：不能谎称存活
        return false
    }

    private fun waitUntilAlive(): Boolean {
        val deadline = System.currentTimeMillis() + ALIVE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isHostServerAlive()) return true
            Thread.sleep(ALIVE_POLL_MS)
        }
        return isHostServerAlive()
    }

    private fun tryRoot(cmd: String): Boolean {
        // 冷启不强制开关：有 su 就用；无 root 很快失败回落。
        return RootBootStarter.execSu(cmd)
    }

    private fun tryShizukuShell(cmd: String): Boolean {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Log.i(TAG, "Shizuku binder not up; skip newProcess bootstrap")
            return false
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            runCatching { Shizuku.requestPermission(0xC4E0) }
            Log.i(TAG, "Shizuku not granted; still try newProcess")
        }
        val out = execShizukuCapture(arrayOf("sh", "-c", cmd))
        val ok = out?.contains(CareMinBootShell.SHELL_BOOT_OK) == true ||
            out?.contains(OneKukuCoreComponent.SHELL_BOOT_OK) == true
        Log.i(TAG, "Shizuku shell ok=$ok out=${out?.take(160)}")
        return ok
    }

    private fun tryWirelessAdb(context: Context): Boolean {
        return runCatching {
            runBlocking {
                // 冷启常比 Wi‑Fi STA 早几秒；先等到有网再试，避免误报 wifi_sta_required。
                if (!OneKukuAdbMdns.isWifiClientConnected(context)) {
                    Log.i(TAG, "wait Wi‑Fi before wireless ADB bootstrap")
                    OneKukuAdbMdns.waitForWifiClient(context, 60_000L)
                }
                when (val wifi = ShizukuSetupHelper.ensureAdbWifiEnabled(context)) {
                    ShizukuSetupHelper.AdbWifiEnsureResult.ENABLED_NOW -> {
                        Log.i(TAG, "adb_wifi enabled now; short wait")
                        kotlinx.coroutines.delay(2_400L)
                    }
                    ShizukuSetupHelper.AdbWifiEnsureResult.ALREADY_ON ->
                        Log.i(TAG, "adb_wifi already on")
                    ShizukuSetupHelper.AdbWifiEnsureResult.FAILED ->
                        Log.w(TAG, "ensureAdbWifi failed")
                }
                when (val o = OneKukuMiniAdbClient.activateExistingOrNeedPair(context)) {
                    is OneKukuMiniAdbClient.Outcome.Success -> {
                        Log.i(TAG, "wireless activate success detail=${o.detail}")
                        true
                    }
                    is OneKukuMiniAdbClient.Outcome.NeedPairingCode -> {
                        // 已配对：多半是重启后 adbd/tcpip 口还没起来，不是真要六位码。
                        if (!OneKukuEmbeddedAdbActivator.hasPairedOnce(context)) {
                            Log.w(TAG, "wireless needs pairing code")
                            return@runBlocking false
                        }
                        Log.i(TAG, "paired: poll adbd port then retry activate")
                        val port = OneKukuAdbEnvironment.pollStartableConnectPort(
                            context,
                            timeoutMs = PAIRED_BOOT_PORT_WAIT_MS,
                            pollMs = 500L,
                        )
                        Log.i(TAG, "paired port poll result=$port")
                        when (
                            val retry = OneKukuMiniAdbClient.activateExistingOrNeedPair(context)
                        ) {
                            is OneKukuMiniAdbClient.Outcome.Success -> {
                                Log.i(TAG, "wireless activate after port poll detail=${retry.detail}")
                                true
                            }
                            else -> {
                                Log.w(TAG, "wireless still not ready after port poll: $retry")
                                false
                            }
                        }
                    }
                    is OneKukuMiniAdbClient.Outcome.Failed -> {
                        Log.w(TAG, "wireless failed reason=${o.reason}")
                        false
                    }
                }
            }
        }.getOrDefault(false)
    }

    private fun execShizukuCapture(cmd: Array<String>): String? {
        return runCatching {
            val m = Shizuku::class.java.methods.firstOrNull { method ->
                method.name == "newProcess" && method.parameterTypes.isNotEmpty()
            } ?: return null
            val process = when (m.parameterTypes.size) {
                1 -> m.invoke(null, cmd) as? Process
                3 -> m.invoke(null, cmd, null, null) as? Process
                else -> m.invoke(null, cmd) as? Process
            } ?: return null
            try {
                val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                process.waitFor(20, TimeUnit.SECONDS)
                text
            } finally {
                runCatching { process.destroy() }
            }
        }.onFailure {
            Log.w(TAG, "Shizuku newProcess failed: ${it.message}")
        }.getOrNull()
    }

    private fun execSuCapture(command: String): String? {
        val candidates = listOf(
            listOf("su", "-c", command),
            listOf("/system/bin/su", "-c", command),
        )
        for (argv in candidates) {
            val text = runCatching {
                val process = ProcessBuilder(argv).redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().use { it.readText() }
                val finished = process.waitFor(8, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                out
            }.getOrNull()
            if (text != null) return text
        }
        return null
    }
}
