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

/**
 * OneKuku 实验：经内嵌无线 ADB 白名单 shell 重跑临时 Root（LD_PRELOAD）。
 * Lite / OneLink 无内嵌 ADB，直接 [Outcome.UnsupportedChannel]。
 *
 * so 先落到公共 Download（shell 可读），再 `cp` 到 `/data/local/tmp`；
 * 若设备上已有 so（例如曾用电脑 adb push），则跳过拷贝。
 */
object OneKukuTempRootActivator {
    private const val TAG = "OneIMS-TempRootAct"
    private const val ASSET_SO = "temproot/preload-comet.so"
    private const val REMOTE_SO = "/data/local/tmp/preload-comet.so"
    private const val PUBLIC_SO_NAME = "oneims-preload-comet.so"
    private const val PUBLIC_SO_SHELL = "/sdcard/Download/$PUBLIC_SO_NAME"
    private const val LD_TIMEOUT_MS = 600_000L

    sealed class Outcome {
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String, val detail: String = "") : Outcome()
        data object UnsupportedChannel : Outcome()
        data object NeedPairing : Outcome()
    }

    suspend fun runExperimental(context: Context): Outcome {
        if (!ChannelLine.usesEmbeddedBridge) {
            return Outcome.UnsupportedChannel
        }
        return withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val device = Build.DEVICE.orEmpty()
            val buildId = Build.ID.orEmpty()
            Log.i(TAG, "start device=$device build=$buildId")

            val probe = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
                context = app,
                command = OneKukuMiniAdbClient.TEMP_ROOT_PROBE_SO,
                timeoutMs = 20_000L,
            )
            if (!probe.ok && probe.reason == "need_pair") {
                return@withContext Outcome.NeedPairing
            }
            if (!probe.ok && probe.reason == "connect_failed") {
                return@withContext Outcome.Failed("adb_connect_failed", probe.reason)
            }
            if (!probe.ok) {
                return@withContext Outcome.Failed(probe.reason.ifBlank { "probe_failed" }, probe.output)
            }

            if (!probe.output.contains("HAS_SO")) {
                if (!stageAssetSoToPublicDownload(app)) {
                    return@withContext Outcome.Failed("stage_so_failed")
                }
                val cpCmd =
                    "cp $PUBLIC_SO_SHELL $REMOTE_SO && chmod 644 $REMOTE_SO && echo CP_OK"
                val copied = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
                    context = app,
                    command = cpCmd,
                    timeoutMs = 60_000L,
                )
                if (!copied.ok || !copied.output.contains("CP_OK")) {
                    return@withContext Outcome.Failed(
                        "so_missing_push_failed",
                        copied.output.ifBlank { copied.reason },
                    )
                }
            }

            val exploit = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
                context = app,
                command = OneKukuMiniAdbClient.TEMP_ROOT_LD_PRELOAD,
                timeoutMs = LD_TIMEOUT_MS,
            )
            if (!exploit.ok) {
                return@withContext Outcome.Failed(
                    exploit.reason.ifBlank { "ld_preload_failed" },
                    exploit.output,
                )
            }

            val verified = verifyRoot(app)
            if (verified) {
                Outcome.Success(
                    "root_ok device=$device build=$buildId out=${exploit.output.take(160)}",
                )
            } else {
                Outcome.Failed(
                    "exploit_ran_but_su_missing",
                    exploit.output.take(240),
                )
            }
        }
    }

    private suspend fun verifyRoot(context: Context): Boolean {
        val a = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
            context = context,
            command = OneKukuMiniAdbClient.TEMP_ROOT_VERIFY_SU_TMP,
            timeoutMs = 15_000L,
        )
        if (a.ok && a.output.contains("uid=0")) return true
        val b = OneKukuEmbeddedAdbActivator.execWhitelistedShell(
            context = context,
            command = OneKukuMiniAdbClient.TEMP_ROOT_VERIFY_SU_APEX,
            timeoutMs = 15_000L,
        )
        return b.ok && b.output.contains("uid=0")
    }

    /** 写入公共 Download，供 adb shell `cp`（应用私有目录 shell 读不到）。 */
    private fun stageAssetSoToPublicDownload(context: Context): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, PUBLIC_SO_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { output ->
                    context.assets.open(ASSET_SO).use { input -> input.copyTo(output) }
                } ?: return false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, PUBLIC_SO_NAME)
                context.assets.open(ASSET_SO).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out.isFile && out.length() > 0L
            }
        }.getOrElse {
            Log.w(TAG, "stage public so failed: ${it.message}")
            false
        }
    }
}
