package com.oneims.app.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 一键临时 Root（实验）：
 * - OneKuku：内嵌无线 ADB 白名单 shell
 * - Lite：外置 Shizuku `sh -c` 白名单 shell（不改 Shizuku Manager）
 *
 * so 先落到公共 Download，再 `cp` 到 `/data/local/tmp`；已存在则跳过。
 * 验活优先看 exploit 输出 + 本机 ProcessBuilder 跑绝对路径 su（不经 Drop-In mock）。
 */
object OneKukuTempRootActivator {
    private const val TAG = "OneIMS-TempRootAct"
    private const val LD_TIMEOUT_MS = 600_000L

    sealed class Outcome {
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String, val detail: String = "") : Outcome()
        data object UnsupportedChannel : Outcome()
        data object UnsupportedDevice : Outcome()
        data object NeedPairing : Outcome()
        data object NeedShizuku : Outcome()
    }

    private data class ShellExec(
        val ok: Boolean,
        val output: String,
        val reason: String = "",
    )

    suspend fun runExperimental(context: Context): Outcome =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            // so：远端优先（见 TempRootSoProvider）；失败再 cache / APK assets。
            val ready = TempRootSoProvider.ensure(app)
                ?: return@withContext Outcome.UnsupportedDevice
            val device = ready.device
            val buildId = ready.buildId
            val via = if (ChannelLine.usesEmbeddedBridge) "embedded_adb" else "shizuku"
            val sourceLabel = when (val src = ready.source) {
                is TempRootSoProvider.SoSource.Asset -> "asset:${src.assetPath}"
                is TempRootSoProvider.SoSource.CachedFile ->
                    if (src.fetchedRemote) "remote:${src.file.name}" else "cache:${src.file.name}"
            }
            Log.i(TAG, "start via=$via device=$device build=$buildId so=$sourceLabel")

            val probe = exec(app, TempRootShellCommands.PROBE_SO, 20_000L)
            when {
                !probe.ok && probe.reason == "need_pair" ->
                    return@withContext Outcome.NeedPairing
                !probe.ok && probe.reason == "need_shizuku" ->
                    return@withContext Outcome.NeedShizuku
                !probe.ok && probe.reason == "connect_failed" ->
                    return@withContext Outcome.Failed("adb_connect_failed", probe.reason)
                !probe.ok ->
                    return@withContext Outcome.Failed(
                        probe.reason.ifBlank { "probe_failed" },
                        probe.output,
                    )
            }

            if (!probe.output.contains("HAS_SO")) {
                val staged = when (val src = ready.source) {
                    is TempRootSoProvider.SoSource.Asset ->
                        stageAssetSoToPublicDownload(app, src.assetPath)
                    is TempRootSoProvider.SoSource.CachedFile ->
                        stageFileSoToPublicDownload(app, src.file)
                }
                if (!staged) {
                    return@withContext Outcome.Failed(
                        "stage_so_failed",
                        sourceLabel,
                    )
                }
                val copied = exec(app, TempRootShellCommands.cpPublicSoToTmp(), 60_000L)
                if (!copied.ok || !copied.output.contains("CP_OK")) {
                    return@withContext Outcome.Failed(
                        "so_missing_push_failed",
                        copied.output.ifBlank { copied.reason },
                    )
                }
            }

            val exploit = exec(app, TempRootShellCommands.LD_PRELOAD, LD_TIMEOUT_MS)
            if (!exploit.ok) {
                return@withContext Outcome.Failed(
                    exploit.reason.ifBlank { "ld_preload_failed" },
                    exploit.output,
                )
            }

            val verified =
                TempRootShellCommands.looksLikeRootSuccess(exploit.output) ||
                    verifyRootHonest(app)
            if (verified) {
                Outcome.Success(
                    "root_ok via=$via device=$device build=$buildId so=$sourceLabel out=${exploit.output.take(120)}",
                )
            } else {
                val suProbe = exec(app, TempRootShellCommands.VERIFY_SU_TMP, 12_000L)
                Outcome.Failed(
                    classifySuMissingReason(exploit.output, suProbe.output),
                    listOf(exploit.output, suProbe.output)
                        .filter { it.isNotBlank() }
                        .joinToString(" | ")
                        .take(280),
                )
            }
        }

    /** 把 SELinux / daemon 拒连从笼统的 su_missing 里拆出来，方便 UI 给可行动提示。 */
    internal fun classifySuMissingReason(exploitOut: String, suOut: String): String {
        val blob = "$exploitOut\n$suOut"
        val denied =
            blob.contains("Permission denied", ignoreCase = true) ||
                blob.contains("connect daemon", ignoreCase = true) ||
                blob.contains("temp_su.sock", ignoreCase = true)
        val enforcing =
            blob.contains("enforce=1", ignoreCase = true) ||
                blob.contains("Enforcing", ignoreCase = true)
        return when {
            denied && enforcing -> "selinux_blocks_su_daemon"
            denied -> "su_daemon_permission_denied"
            enforcing && !TempRootShellCommands.looksLikeRootSuccess(exploitOut) ->
                "exploit_failed_under_enforcing"
            else -> "exploit_ran_but_su_missing"
        }
    }

    private suspend fun exec(
        context: Context,
        command: String,
        timeoutMs: Long,
    ): ShellExec {
        return if (ChannelLine.usesEmbeddedBridge) {
            val r = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
                context = context,
                command = command,
                timeoutMs = timeoutMs,
            )
            ShellExec(r.ok, r.output, r.reason)
        } else {
            val r = ShizukuTempRootShell.execWhitelistedShell(
                command = command,
                timeoutMs = timeoutMs,
            )
            ShellExec(r.ok, r.output, r.reason)
        }
    }

    /**
     * 诚实验活：优先本机 ProcessBuilder 绝对路径 su + /system/bin/id。
     * Lite：再经 Shizuku 白名单跑同一条 VERIFY（不依赖 app 沙箱能否看到 /data/local/tmp/su）。
     * OneKuku：再经内嵌 adbd 白名单复核。
     */
    private suspend fun verifyRootHonest(context: Context): Boolean {
        if (localSuIdOk()) return true
        if (RootPresenceProbe.probe().any) return true
        if (!ChannelLine.usesEmbeddedBridge) {
            val viaShizukuTmp = ShizukuTempRootShell.execWhitelistedShell(
                TempRootShellCommands.VERIFY_SU_TMP,
                15_000L,
            )
            if (TempRootShellCommands.looksLikeRootSuccess(viaShizukuTmp.output)) return true
            val viaShizukuApex = ShizukuTempRootShell.execWhitelistedShell(
                TempRootShellCommands.VERIFY_SU_APEX,
                15_000L,
            )
            return TempRootShellCommands.looksLikeRootSuccess(viaShizukuApex.output)
        }
        val a = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
            context,
            TempRootShellCommands.VERIFY_SU_TMP,
            15_000L,
        )
        if (a.ok && TempRootShellCommands.looksLikeRootSuccess(a.output)) return true
        val b = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
            context,
            TempRootShellCommands.VERIFY_SU_APEX,
            15_000L,
        )
        return b.ok && TempRootShellCommands.looksLikeRootSuccess(b.output)
    }

    private fun localSuIdOk(): Boolean {
        val candidates = listOf(
            "/data/local/tmp/su",
            "/apex/com.android.virt/bin/su",
        )
        return candidates.any { suPath ->
            runCatching {
                if (!File(suPath).exists()) return@runCatching false
                val process = ProcessBuilder(suPath, "-c", "/system/bin/id")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val finished = process.waitFor(5, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@runCatching false
                }
                process.exitValue() == 0 &&
                    TempRootShellCommands.looksLikeRootSuccess(output)
            }.getOrDefault(false)
        }
    }

    private fun stageAssetSoToPublicDownload(context: Context, assetPath: String): Boolean {
        return stageToPublicDownload(context) { output ->
            context.assets.open(assetPath).use { input -> input.copyTo(output) }
        }
    }

    private fun stageFileSoToPublicDownload(context: Context, file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return stageToPublicDownload(context) { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun stageToPublicDownload(
        context: Context,
        write: (java.io.OutputStream) -> Unit,
    ): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, TempRootShellCommands.PUBLIC_SO_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { output -> write(output) } ?: return false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, TempRootShellCommands.PUBLIC_SO_NAME)
                out.outputStream().use { output -> write(output) }
                out.isFile && out.length() > 0L
            }
        }.getOrElse {
            Log.w(TAG, "stage public so failed: ${it.message}")
            false
        }
    }
}
