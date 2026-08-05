package com.oneims.app.core

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 教程同构：有 Root 时备份 phone 目录下已有 `carrierconfig-*.xml` 到公共 Download。
 */
object TempRootCarrierBackup {
    private const val TAG = "OneIMS-TempRootCcBak"
    private const val REMOTE_BASE = "/data/user_de/0/com.android.phone/files"

    data class Result(
        val success: Boolean,
        val message: String,
        val path: String? = null,
        val fileCount: Int = 0,
    )

    fun backup(context: Context): Result {
        if (!RootPresenceProbe.probe().any) {
            return Result(false, "no_root")
        }
        val su = resolveWorkingSu() ?: return Result(false, "su_unavailable")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "oneims-cc-backup-$stamp",
        )
        val fallbackDir = File(context.getExternalFilesDir(null), "oneims-cc-backup-$stamp")
        val dest = if (publicDir.parentFile?.exists() == true || publicDir.parentFile?.mkdirs() == true) {
            publicDir
        } else {
            fallbackDir
        }
        dest.mkdirs()

        val listOut = execSuCapture(su, "ls -1 $REMOTE_BASE/carrierconfig-*.xml 2>/dev/null || true")
            ?: return Result(false, "list_failed")
        val remoteFiles = listOut.lines()
            .map { it.trim() }
            .filter { it.startsWith(REMOTE_BASE) && it.endsWith(".xml") }
        if (remoteFiles.isEmpty()) {
            return Result(false, "no_carrierconfig_xml")
        }

        var count = 0
        for (remote in remoteFiles) {
            val name = remote.substringAfterLast('/')
            val raw = execSuCapture(su, "cat '$remote'") ?: continue
            File(dest, name).writeText(raw)
            count++
        }
        return if (count > 0) {
            Log.i(TAG, "backed up $count files -> ${dest.absolutePath}")
            Result(true, "ok", path = dest.absolutePath, fileCount = count)
        } else {
            Result(false, "copy_none")
        }
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
                return@runCatching null
            }
            if (process.exitValue() != 0) return@runCatching null
            output
        }.getOrNull()
    }
}
