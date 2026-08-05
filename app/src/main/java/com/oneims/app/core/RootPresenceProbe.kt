package com.oneims.app.core

import com.oneims.app.core.privilege.PrivilegeBridges
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 探测临时 / 永久 Root，供首页 ROOT 徽标与「临时 Root 持久化改运营商」开关显隐。
 *
 * - 永久：Magisk / KernelSU 痕迹，或系统路径 `su` 可用
 * - 临时：特权桥 uid=0，或临时 `su` **可执行且 uid=0**（仅文件残留不算）
 */
object RootPresenceProbe {

    data class Snapshot(
        val temporary: Boolean,
        val permanent: Boolean,
    ) {
        val any: Boolean get() = temporary || permanent
        /** 右上角 ROOT 标签：有真实 Root（临时或永久）才显示。 */
        val showRootBadge: Boolean get() = any
        /** 首页 Root 功能区：有真实 Root 才显示。 */
        val showCarrierXmlSwitch: Boolean get() = any
        /** 徽标样式：永久优先于临时。 */
        val badgePermanent: Boolean get() = permanent
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
        val permanentSu = permanentSuCandidates.any { canExecSu(it) }
        val temporarySu = temporarySuCandidates.any { canExecSu(it) }
        return resolve(bridgeRoot, markerPermanent, permanentSu, temporarySu)
    }

    /**
     * 纯判定（可单测）：不再把「临时 su 路径存在」当成 Root。
     */
    internal fun resolve(
        bridgeRoot: Boolean,
        markerPermanent: Boolean,
        permanentSu: Boolean,
        temporarySu: Boolean,
    ): Snapshot {
        val permanent = markerPermanent || permanentSu
        val temporary = bridgeRoot || temporarySu
        return Snapshot(temporary = temporary, permanent = permanent)
    }

    private fun pathExists(path: String): Boolean =
        runCatching { File(path).exists() }.getOrDefault(false)

    private fun canExecSu(suPath: String): Boolean {
        return runCatching {
            // 绝对路径 id，避免 Drop-In 对裸 `id` 的假 Root mock
            val process = ProcessBuilder(suPath, "-c", "/system/bin/id")
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
