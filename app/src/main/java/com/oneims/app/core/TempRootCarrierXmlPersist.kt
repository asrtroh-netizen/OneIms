package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.telephony.SubscriptionManager
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 教程同构：写入最小网络键到 `carrierconfig-*.xml`，并 `chown radio`。
 *
 * 优先改已存在文件；若电话服务从未落盘 XML（常见于干净 Pixel），
 * 则按当前 SIM 安全 seed 最小 `<bundle/>` 后再补丁（仍不碰分区镜像）。
 */
object TempRootCarrierXmlPersist {
    private const val TAG = "OneIMS-TempRootCcXml"
    private const val REMOTE_BASE = "/data/user_de/0/com.android.phone/files"
    private const val STAGE_DIR = "/data/local/tmp/oneims-carrierconfig-staged"
    private const val EMPTY_BUNDLE =
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<bundle>\n</bundle>\n"

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
        // 不单靠 App 沙箱 probe：有可用 su 即可（与首页徽标特权复核一致）
        if (!RootPresenceProbe.probe().any && resolveWorkingSu() == null) {
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

        var remoteFiles = listCarrierConfigFiles(su)
        var seeded = 0
        if (remoteFiles.isEmpty()) {
            seeded = seedMissingCarrierConfigFiles(context, su)
            if (seeded <= 0) {
                return ApplyResult(true, false, 0, "no_carrierconfig_xml")
            }
            remoteFiles = listCarrierConfigFiles(su)
            if (remoteFiles.isEmpty()) {
                return ApplyResult(true, false, 0, "no_carrierconfig_xml")
            }
            Log.i(TAG, "seeded $seeded carrierconfig xml before patch")
        }

        val stagedLocal = File(context.cacheDir, "oneims-cc-stage").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            var patched = 0
            var alreadyOk = 0
            var writeFailed = 0
            for (remote in remoteFiles) {
                val name = remote.substringAfterLast('/')
                val raw = execSuCapture(su, "cat '$remote'")
                if (raw == null) {
                    writeFailed++
                    Log.w(TAG, "cat failed $name")
                    continue
                }
                val next = CarrierConfigXmlMinimalPatcher.patch(
                    original = raw,
                    displayCarrierName = displayCarrierName,
                )
                if (next == raw) {
                    // 幂等：键已是目标态，不算失败（否则会「参考失败 + 我的成功」误报）
                    alreadyOk++
                    Log.i(TAG, "already ok $name")
                    continue
                }
                val local = File(stagedLocal, name)
                local.writeText(next)
                val pushOk = pushViaSu(su, local, "$STAGE_DIR/$name")
                if (!pushOk) {
                    writeFailed++
                    Log.w(TAG, "stage push failed $name")
                    continue
                }
                val install = buildInstallSnippet(name, allowCreate = true)
                val installed = execSuCapture(su, install)?.contains("ok:$name") == true
                if (installed) {
                    patched++
                } else {
                    writeFailed++
                    Log.w(TAG, "install failed $name")
                }
            }
            if (patched > 0 && restartPhone) {
                execSuCapture(su, "killall com.android.phone 2>/dev/null || true")
            }
            // 有写入成功，或全部已是目标态且无写失败 → 成功
            val ok = patched > 0 || (alreadyOk > 0 && writeFailed == 0)
            val msg = when {
                patched > 0 && seeded > 0 -> "xml_patched=$patched,seeded=$seeded"
                patched > 0 && alreadyOk > 0 -> "xml_patched=$patched,already=$alreadyOk"
                patched > 0 -> "xml_patched=$patched"
                ok && alreadyOk > 0 -> "xml_already_ok=$alreadyOk"
                else -> "xml_patch_none"
            }
            Log.i(TAG, msg)
            return ApplyResult(true, ok, patched, msg)
        } finally {
            stagedLocal.deleteRecursively()
        }
    }

    private fun listCarrierConfigFiles(su: String): List<String> {
        val listOut = execSuCapture(
            su,
            "ls -1 $REMOTE_BASE/carrierconfig-*.xml 2>/dev/null || true",
        ) ?: return emptyList()
        return listOut.lines()
            .map { it.trim() }
            .filter { it.endsWith(".xml") && it.contains("carrierconfig-") }
            .map { line ->
                if (line.startsWith("/")) line else "$REMOTE_BASE/${line.substringAfterLast('/')}"
            }
            .distinct()
    }

    @SuppressLint("MissingPermission")
    private fun seedMissingCarrierConfigFiles(context: Context, su: String): Int {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return 0
        val subs = runCatching { sm.activeSubscriptionInfoList }.getOrNull().orEmpty()
        if (subs.isEmpty()) {
            Log.w(TAG, "seed skipped: no active subscriptions")
            return 0
        }
        // Android 常对普通 App 清空 SubscriptionInfo.iccId；有 Root 时从 telephony.db 回退。
        val (iccidBySubId, carrierIdBySubId) = loadSimIdentityFromTelephonyDb(context, su)
        var created = 0
        for (sub in subs) {
            val subId = sub.subscriptionId
            val iccid = sub.iccId?.trim().orEmpty().ifEmpty {
                iccidBySubId[subId].orEmpty()
            }
            if (iccid.isEmpty()) {
                Log.w(TAG, "seed skip subId=$subId: empty iccid (sm+db)")
                continue
            }
            val carrierId = when {
                sub.carrierId > 0 -> sub.carrierId
                else -> carrierIdBySubId[subId] ?: -1
            }
            val name = "carrierconfig-com.google.android.carrier-$iccid-$carrierId.xml"
            val local = File(context.cacheDir, "oneims-cc-seed-$name")
            try {
                local.writeText(EMPTY_BUNDLE)
                if (!pushViaSu(su, local, "$STAGE_DIR/$name")) {
                    Log.w(TAG, "seed stage failed $name")
                    continue
                }
                val installed = execSuCapture(su, buildInstallSnippet(name, allowCreate = true))
                    ?.contains("ok:$name") == true
                if (installed) {
                    created++
                    Log.i(TAG, "seeded $name for subId=$subId")
                } else {
                    Log.w(TAG, "seed install failed $name")
                }
            } finally {
                local.delete()
            }
        }
        return created
    }

    private fun loadSimIdentityFromTelephonyDb(
        context: Context,
        su: String,
    ): Pair<Map<Int, String>, Map<Int, Int>> {
        val localDb = File(context.cacheDir, "oneims-telephony-seed.db")
        val uid = Process.myUid()
        val copy = execSuCapture(
            su,
            """
                set -e
                src=/data/user_de/0/com.android.providers.telephony/databases/telephony.db
                dst='${localDb.absolutePath}'
                cp -f "${'$'}src" "${'$'}dst"
                chown $uid:$uid "${'$'}dst" 2>/dev/null || chown $uid "${'$'}dst" 2>/dev/null || true
                chmod 600 "${'$'}dst" 2>/dev/null || chmod 644 "${'$'}dst"
                echo COPIED
            """.trimIndent(),
        )
        if (copy?.contains("COPIED") != true || !localDb.isFile) {
            Log.w(TAG, "telephony.db copy failed: ${(copy ?: "").take(160)}")
            return emptyMap<Int, String>() to emptyMap()
        }
        val iccids = linkedMapOf<Int, String>()
        val carriers = linkedMapOf<Int, Int>()
        runCatching {
            SQLiteDatabase.openDatabase(
                localDb.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery(
                    "SELECT _id, icc_id, carrier_id FROM siminfo WHERE icc_id IS NOT NULL AND icc_id != ''",
                    null,
                ).use { c ->
                    val iId = c.getColumnIndexOrThrow("_id")
                    val iIcc = c.getColumnIndexOrThrow("icc_id")
                    val iCid = c.getColumnIndexOrThrow("carrier_id")
                    while (c.moveToNext()) {
                        val subId = c.getInt(iId)
                        val icc = c.getString(iIcc)?.trim().orEmpty()
                        if (icc.isNotEmpty()) {
                            iccids[subId] = icc
                        }
                        if (!c.isNull(iCid)) {
                            carriers[subId] = c.getInt(iCid)
                        }
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "telephony.db parse failed: ${err.message}")
        }
        localDb.delete()
        Log.i(TAG, "telephony.db iccid hits=${iccids.size}")
        return iccids to carriers
    }

    private fun buildInstallSnippet(name: String, allowCreate: Boolean): String {
        val src = "$STAGE_DIR/$name"
        val dst = "$REMOTE_BASE/$name"
        val bak = "/data/local/tmp/oneims-cc-bak-$name"
        val requireDst = if (allowCreate) {
            "mkdir -p '$REMOTE_BASE'"
        } else {
            "[ -f '$dst' ]"
        }
        return """
            set -eu
            [ -f '$src' ]
            $requireDst
            if [ -f '$dst' ]; then cp '$dst' '$bak'; fi
            cp '$src' '$dst'
            chown radio:radio '$dst'
            chmod 0600 '$dst'
            if command -v restorecon >/dev/null 2>&1; then
              restorecon '$dst' || chcon u:object_r:radio_data_file:s0 '$dst'
            else
              chcon u:object_r:radio_data_file:s0 '$dst' || true
            fi
            cmp -s '$src' '$dst'
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
