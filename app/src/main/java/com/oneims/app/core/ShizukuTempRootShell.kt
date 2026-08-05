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
                        text.contains("CP_OK") ||
                        text.contains("KILL_OK")
                    ) {
                        // 探测/拷贝/清残留可早退；exploit 成功也可早退
                        if (text.contains("HAS_SO") || text.contains("NO_SO") ||
                            text.contains("CP_OK") ||
                            text.contains("KILL_OK") ||
                            TempRootShellCommands.looksLikeRootSuccess(text)
                        ) {
                            break
                        }
                    }
                    // 不可用 Process.isAlive：ShizukuRemoteProcess.exitValue 会把
                    // IllegalThreadStateException 包成 RuntimeException("process hasn't exited")。
                    if (!isRemoteProcessAlive(process)) break
                    Thread.sleep(80)
                }
                if (isRemoteProcessAlive(process)) {
                    val finished = waitRemoteProcess(
                        process,
                        (deadline - System.currentTimeMillis()).coerceAtLeast(1L),
                    )
                    if (!finished) {
                        runCatching { process.destroy() }
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
                val exit = runCatching { process.exitValue() }.getOrDefault(-1)
                val probeOrCp =
                    text.contains("HAS_SO") ||
                        text.contains("NO_SO") ||
                        text.contains("CP_OK") ||
                        text.contains("KILL_OK")
                val rootish = TempRootShellCommands.looksLikeRootSuccess(text)
                val needsRoot =
                    command.trim() == TempRootShellCommands.LD_PRELOAD ||
                        command.trim() == TempRootShellCommands.VERIFY_SU_TMP ||
                        command.trim() == TempRootShellCommands.VERIFY_SU_APEX ||
                        (
                            command.contains("LD_PRELOAD=") &&
                                command.trim() != TempRootShellCommands.KILL_STUCK_PRELOAD
                            )
                // 探测/拷贝/清残留看标记；exploit/验活必须看到 uid=0，禁止「exit=0 + 一堆日志」假成功。
                val ok = when {
                    needsRoot -> rootish
                    probeOrCp -> true
                    else -> rootish || (exit == 0 && text.isNotBlank())
                }
                Log.i(TAG, "shizuku shell exit=$exit ok=$ok needsRoot=$needsRoot out=${text.take(400)}")
                ShellExecResult(
                    ok = ok,
                    output = text,
                    reason = when {
                        ok -> "ok"
                        needsRoot && !rootish -> "no_uid0_in_output"
                        else -> "shell_failed_exit_$exit"
                    },
                )
            } catch (t: Throwable) {
                Log.w(TAG, "shizuku shell failed: ${t.message}")
                ShellExecResult(false, "", t.message ?: "exec_failed")
            } finally {
                runCatching { process.destroy() }
            }
        }

    /**
     * Shizuku API 13+ 的 [Shizuku.newProcess] 是 **private static**，
     * `Class.methods`（仅 public）永远拿不到 → 旧实现会稳定落到 new_process_unavailable。
     */
    private fun newProcess(cmd: Array<String>): Process? {
        return runCatching {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            m.isAccessible = true
            m.invoke(null, cmd, null, null) as? Process
        }.onFailure { t ->
            Log.w(TAG, "Shizuku.newProcess unavailable: ${t.javaClass.simpleName}: ${t.message}")
        }.getOrNull()
    }

    /** 优先 ShizukuRemoteProcess.alive()，避免 Process.isAlive → exitValue 抛 RuntimeException。 */
    private fun isRemoteProcessAlive(process: Process): Boolean {
        return runCatching {
            val m = process.javaClass.getMethod("alive")
            m.invoke(process) as Boolean
        }.getOrElse {
            runCatching { process.isAlive }.getOrDefault(true)
        }
    }

    private fun waitRemoteProcess(process: Process, timeoutMs: Long): Boolean {
        return runCatching {
            val m = process.javaClass.getMethod(
                "waitForTimeout",
                Long::class.javaPrimitiveType,
                TimeUnit::class.java,
            )
            m.invoke(process, timeoutMs, TimeUnit.MILLISECONDS) as Boolean
        }.getOrElse {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }
}
