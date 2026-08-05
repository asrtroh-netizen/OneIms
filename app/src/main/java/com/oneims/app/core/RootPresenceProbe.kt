package com.oneims.app.core

import com.oneims.app.core.privilege.PrivilegeBridges
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 探测临时 / 永久 Root，供首页 ROOT 徽标与「临时 Root 持久化改运营商」开关显隐。
 *
 * - 永久：Magisk / KernelSU 痕迹，或系统路径 `su` 可用
 * - 临时：特权桥 uid=0，或教程常见临时 `su` 路径存在/可用
 */
object RootPresenceProbe {

    data class Snapshot(
        val temporary: Boolean,
        val permanent: Boolean,
    ) {
        val any: Boolean get() = temporary || permanent
        /** 右上角 ROOT 标签：临时或永久任一成立即显示。 */
        val showRootBadge: Boolean get() = any
        /**
         * 首页第三行开关：有可用 Root 才显示。
         * （产品诉求含临时 Root 写 CarrierConfig；严格「仅永久」会挡住 Fold 临时 Root。）
         */
        val showCarrierXmlSwitch: Boolean get() = any
    }

    private val permanentSuCandidates = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
    )

    private val temporarySuCandidates = listOf(
        "/data/local/tmp/su",
        "/apex/com.android.virt/bin/su",
    )

    private val permanentMarkers = listOf(
        "/data/adb/magisk",
        "/sbin/magisk",
        "/data/adb/ksu",
        "/data/adb/ksud",
    )

    fun probe(): Snapshot {
        val bridgeRoot = runCatching { PrivilegeBridges.current.getUid() == 0 }.getOrDefault(false)
        val markerPermanent = permanentMarkers.any { pathExists(it) }
        val tempPath = temporarySuCandidates.any { pathExists(it) }
        val permanentSu = permanentSuCandidates.any { canExecSu(it) }
        val temporarySu = temporarySuCandidates.any { canExecSu(it) }

        val permanent = markerPermanent || permanentSu
        val temporary = bridgeRoot || temporarySu || (tempPath && !permanent)
        return Snapshot(temporary = temporary, permanent = permanent)
    }

    private fun pathExists(path: String): Boolean =
        runCatching { File(path).exists() }.getOrDefault(false)

    private fun canExecSu(suPath: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(suPath, "-c", "id")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching false
            }
            process.exitValue() == 0 && output.contains("uid=0")
        }.getOrDefault(false)
    }
}
