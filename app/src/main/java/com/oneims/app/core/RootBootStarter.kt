package com.oneims.app.core

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Root 开机旁路：开关开启时用 `su -c` 拉起特权通道。
 *
 * - OneKuku：执行 [OneKukuCoreComponent.bridgeBootShellCommand] 拉起 onebridge_server
 * - OneLink：不在本进程冒充 Shizuku Root 启动（由 Shizuku App 自己的开机 Root 开关负责）
 *
 * 失败静默回落现有 Boot 重放 / 无线调试路径，不改非 Root 主逻辑。
 */
object RootBootStarter {
    private const val TAG = "OneIMS-RootBoot"

    fun maybeStartOnBoot(context: Context) {
        if (!ConfigStore.isRootBootStart(context)) {
            Log.i(TAG, "root boot switch off; skip")
            return
        }
        if (ChannelLine.usesShizuku) {
            Log.i(TAG, "onelink: root boot is owned by Shizuku app; skip OneIMS su start")
            return
        }
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand(
            packageName = context.packageName,
            forceRestart = false,
        )
        if (cmd.isBlank()) {
            Log.w(TAG, "empty bridge boot command")
            return
        }
        val ok = execSu(cmd)
        Log.i(TAG, "su bridge boot ok=$ok")
    }

    /**
     * @return true 若进程退出码为 0，或输出含成功标记
     */
    internal fun execSu(command: String): Boolean {
        val candidates = listOf(
            listOf("su", "-c", command),
            listOf("/system/bin/su", "-c", command),
            listOf("/system/xbin/su", "-c", command),
        )
        for (argv in candidates) {
            val result = runCatching {
                val process = ProcessBuilder(argv)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val finished = process.waitFor(20, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    Log.w(TAG, "su timed out via ${argv.first()}")
                    return@runCatching false
                }
                val code = process.exitValue()
                val markerOk = output.contains(OneKukuCoreComponent.SHELL_BOOT_OK) ||
                    output.contains("OneBridge_started")
                Log.i(TAG, "su via=${argv.first()} code=$code markerOk=$markerOk out=${output.take(200)}")
                code == 0 || markerOk
            }.getOrElse { error ->
                Log.w(TAG, "su via ${argv.first()} failed: ${error.message}")
                false
            }
            if (result) return true
        }
        return false
    }
}
