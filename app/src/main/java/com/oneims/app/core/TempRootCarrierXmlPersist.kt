package com.oneims.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 教程同构：在已有 `carrierconfig-*.xml` 上写入最小网络键，并 `chown radio`。
 *
 * 仅覆盖已存在文件，绝不新建；失败不抬升为业务硬失败（由调用方决定是否提示）。
 */
object TempRootCarrierXmlPersist {
    private const val TAG = "OneIMS-TempRootCcXml"
    private const val REMOTE_BASE = "/data/user_de/0/com.android.phone/files"
    private const val STAGE_DIR = "/data/local/tmp/oneims-carrierconfig-staged"

    data class ApplyResult(
        val attempted: Boolean,
        val success: Boolean,
        val patchedCount: Int,
        val message: String,
    )

    fun applyMinimalNetworkIfEnabled(
        context: Context,
        restartPhone: Boolean,
        displayCarrierName: String? = null,
    ): ApplyResult {
        if (!ConfigStore.isRootPersistEnhance(context)) {
            return ApplyResult(false, false, 0, "switch_off")
        }
        if (!RootPresenceProbe.probe().any) {
            return ApplyResult(false, false, 0, "no_root")
        }
        return applyMinimalNetwork(context, restartPhone, displayCarrierName)
    }

    fun applyMinimalNetwork(
        context: Context,
        restartPhone: Boolean,
        displayCarrierName: String? = null,
    ): ApplyResult {
        val su = resolveWorkingSu()
            ?: return ApplyResult(true, false, 0, "su_unavailable")

        val listOut = execSuCapture(su, "ls -1 $REMOTE_BASE/carrierconfig-*.xml 2>/dev/null || true")
            ?: return ApplyResult(true, false, 0, "list_failed")
        val remoteFiles = listOut.lines()
            .map { it.trim() }
            .filter { it.startsWith(REMOTE_BASE) && it.endsWith(".xml") }
        if (remoteFiles.isEmpty()) {
            return ApplyResult(true, false, 0, "no_carrierconfig_xml")
        }

        val stagedLocal = File(context.cacheDir, "oneims-cc-stage").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            var patched = 0
            for (remote in remoteFiles) {
                val name = remote.substringAfterLast('/')
                val raw = execSuCapture(su, "cat '$remote'") ?: continue
                val next = CarrierConfigXmlMinimalPatcher.patch(
                    original = raw,
                    displayCarrierName = displayCarrierName,
                )
                if (next == raw) {
                    Log.i(TAG, "skip unchanged $name")
                    continue
                }
                val local = File(stagedLocal, name)
                local.writeText(next)
                val pushOk = pushViaSu(su, local, "$STAGE_DIR/$name")
                if (!pushOk) {
                    Log.w(TAG, "stage push failed $name")
                    continue
                }
                val install = buildInstallSnippet(name, restartPhone = false)
                val installed = execSuCapture(su, install)?.contains("ok:$name") == true
                if (installed) {
                    patched++
                } else {
                    Log.w(TAG, "install failed $name")
                }
            }
            if (patched > 0 && restartPhone) {
                execSuCapture(su, "killall com.android.phone 2>/dev/null || true")
            }
            val ok = patched > 0
            val msg = if (ok) "xml_patched=$patched" else "xml_patch_none"
            Log.i(TAG, msg)
            return ApplyResult(true, ok, patched, msg)
        } finally {
            stagedLocal.deleteRecursively()
        }
    }

    private fun buildInstallSnippet(name: String, restartPhone: Boolean): String {
        val restart = if (restartPhone) {
            "killall com.android.phone 2>/dev/null || true"
        } else {
            ":"
        }
        val src = "$STAGE_DIR/$name"
        val dst = "$REMOTE_BASE/$name"
        val bak = "/data/local/tmp/oneims-cc-bak-$name"
        return """
            set -eu
            [ -f '$src' ]
            [ -f '$dst' ]
            cp '$dst' '$bak'
            cp '$src' '$dst'
            chown radio:radio '$dst'
            chmod 0600 '$dst'
            if command -v restorecon >/dev/null 2>&1; then
              restorecon '$dst' || chcon u:object_r:radio_data_file:s0 '$dst'
            else
              chcon u:object_r:radio_data_file:s0 '$dst' || true
            fi
            cmp -s '$src' '$dst'
            $restart
            echo ok:$name
        """.trimIndent()
    }

    private fun pushViaSu(su: String, local: File, remote: String): Boolean {
        // su 可读应用私有 cache，避免巨型 base64 塞进 argv。
        val script = """
            set -eu
            mkdir -p $STAGE_DIR
            cp '${local.absolutePath}' '$remote'
            [ -s '$remote' ]
            echo staged
        """.trimIndent()
        return execSuCapture(su, script)?.contains("staged") == true
    }

    private fun resolveWorkingSu(): String? {
        val candidates = listOf(
            "/data/local/tmp/su",
            "/apex/com.android.virt/bin/su",
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
        )
        return candidates.firstOrNull { canExecSu(it) }
    }

    private fun canExecSu(suPath: String): Boolean {
        val out = execSuCapture(suPath, "id") ?: return false
        return out.contains("uid=0")
    }

    private fun execSuCapture(suPath: String, command: String): String? {
        return runCatching {
            val process = ProcessBuilder(suPath, "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(45, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG, "su timed out via $suPath")
                return@runCatching null
            }
            if (process.exitValue() != 0) {
                Log.w(TAG, "su exit=${process.exitValue()} via=$suPath out=${output.take(240)}")
                return@runCatching null
            }
            output
        }.getOrElse { error ->
            Log.w(TAG, "su via $suPath failed: ${error.message}")
            null
        }
    }
}
