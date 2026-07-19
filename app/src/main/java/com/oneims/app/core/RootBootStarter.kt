package com.oneims.app.core

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Root 开机旁路：开关开启时用 `su -c` 拉起**本产品对应**的特权桥。
 *
 * - OneIMS / OneKuku：拉起 `onebridge_server`（内嵌 OneBridge）
 * - OneIMS Lite / OneLink：拉起已安装 Shizuku 的 `libshizuku.so`（对齐官方 Root 启动命令）
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
        val cmd = resolveBootCommand(context)
        if (cmd.isNullOrBlank()) {
            Log.w(TAG, "no root boot command for channel=${ChannelLine.id}")
            return
        }
        val ok = execSu(cmd)
        Log.i(TAG, "su privilege boot channel=${ChannelLine.id} ok=$ok")
    }

    private fun resolveBootCommand(context: Context): String? {
        return if (ChannelLine.usesEmbeddedBridge) {
            val cmd = OneKukuCoreComponent.bridgeBootShellCommand(
                packageName = context.packageName,
                forceRestart = false,
            )
            cmd.takeIf { it.isNotBlank() }
        } else {
            ShizukuSetupHelper.buildShizukuRootStartCommand(context)
        }
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
                val finished = process.waitFor(25, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    Log.w(TAG, "su timed out via ${argv.first()}")
                    return@runCatching false
                }
                val code = process.exitValue()
                val markerOk = output.contains(OneKukuCoreComponent.SHELL_BOOT_OK) ||
                    output.contains("OneBridge_started") ||
                    output.contains("info: shizuku_started") ||
                    output.contains("shizuku_starter")
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
