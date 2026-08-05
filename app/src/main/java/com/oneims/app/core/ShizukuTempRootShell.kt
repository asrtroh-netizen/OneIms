package com.oneims.app.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Lite：经外置 Shizuku 执行临时 Root 白名单 shell。
 * 用 `sh -c` + 绝对路径，降低 Drop-In su_bridge 对裸 `id`/`su` 的假阳性干扰。
 */
object ShizukuTempRootShell {
    private const val TAG = "OneIMS-ShizukuTempRoot"

    data class ShellExecResult(
        val ok: Boolean,
        val output: String,
        val reason: String = "",
    )

    fun isReady(): Boolean =
        runCatching {
            OneKukuManager.isRunning() && OneKukuManager.isGranted()
        }.getOrDefault(false)

    suspend fun execWhitelistedShell(
        command: String,
        timeoutMs: Long = 120_000L,
    ): ShellExecResult =
        withContext(Dispatchers.IO) {
            if (!TempRootShellCommands.isWhitelisted(command)) {
                return@withContext ShellExecResult(false, "", "not_whitelisted")
            }
            if (!isReady()) {
                return@withContext ShellExecResult(false, "", "need_shizuku")
            }
            val process = newProcess(arrayOf("sh", "-c", command))
                ?: return@withContext ShellExecResult(false, "", "new_process_unavailable")
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                val collected = StringBuilder()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (System.currentTimeMillis() < deadline) {
                    while (reader.ready()) {
                        val line = reader.readLine() ?: break
                        collected.append(line).append('\n')
                    }
                    val text = collected.toString()
                    if (TempRootShellCommands.looksLikeRootSuccess(text) ||
                        text.contains("HAS_SO") ||
                        text.contains("NO_SO") ||
                        text.contains("CP_OK")
                    ) {
                        // 探测/拷贝可早退；exploit 成功也可早退
                        if (text.contains("HAS_SO") || text.contains("NO_SO") ||
                            text.contains("CP_OK") ||
                            TempRootShellCommands.looksLikeRootSuccess(text)
                        ) {
                            break
                        }
                    }
                    if (!process.isAlive) break
                    Thread.sleep(80)
                }
                if (process.isAlive) {
                    val finished = process.waitFor(
                        (deadline - System.currentTimeMillis()).coerceAtLeast(1L),
                        TimeUnit.MILLISECONDS,
                    )
                    if (!finished) {
                        process.destroyForcibly()
                        return@withContext ShellExecResult(
                            false,
                            collected.toString(),
                            "timeout",
                        )
                    }
                }
                // 排空残留
                while (true) {
                    val line = reader.readLine() ?: break
                    collected.append(line).append('\n')
                }
                val text = collected.toString()
                Log.i(TAG, "shizuku shell out=${text.take(400)}")
                ShellExecResult(ok = true, output = text, reason = "ok")
            } catch (t: Throwable) {
                Log.w(TAG, "shizuku shell failed: ${t.message}")
                ShellExecResult(false, "", t.message ?: "exec_failed")
            } finally {
                runCatching { process.destroy() }
            }
        }

    private fun newProcess(cmd: Array<String>): Process? {
        return runCatching {
            val m = Shizuku::class.java.methods.firstOrNull { method ->
                method.name == "newProcess" && method.parameterTypes.isNotEmpty()
            } ?: return null
            val args = when (m.parameterTypes.size) {
                1 -> arrayOf<Any?>(cmd)
                3 -> arrayOf<Any?>(cmd, null, null)
                else -> arrayOf<Any?>(cmd)
            }
            m.invoke(null, *args) as? Process
        }.getOrNull()
    }
}
