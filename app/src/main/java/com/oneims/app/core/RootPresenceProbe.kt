package com.oneims.app.core

import android.content.Context
import com.oneims.app.core.privilege.PrivilegeBridges
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 探测临时 / 永久 Root，供首页 ROOT 徽标与「临时 Root 持久化改运营商」开关显隐。
 *
 * - 永久：Magisk / KernelSU 痕迹，或系统路径 `su` 可用 → 黑金徽标
 * - 临时：特权桥 uid=0，或临时 `su` **可执行且 uid=0**（仅文件残留不算）→ 琥珀徽标
 *
 * App 沙箱经常被 SELinux 拦住直接 `ProcessBuilder(/data/local/tmp/su)`，
 * 因此 [probeWithPrivilege] 会再经 OneKuku / Shizuku 白名单 shell 复核（与一键 Root 验活同路）。
 */
object RootPresenceProbe {

    data class Snapshot(
        val temporary: Boolean,
        val permanent: Boolean,
    ) {
        val any: Boolean get() = temporary || permanent
        /** 右上角 ROOT 标签：有真实 Root（临时或永久）才显示。 */
        val showRootBadge: Boolean get() = any
        /** 首页 Root 功能区（运营商写入 / 工具）：有真实 Root 才显示。 */
        val showCarrierXmlSwitch: Boolean get() = any
        /**
         * 「Root 开机自启」：仅永久 Root 显示。
         * 临时 Root 重启即丢，开机拉起语义不成立，避免误导。
         */
        val showRootBootStart: Boolean get() = permanent
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
     * 本地探测 + 特权通道复核。PC OneRoot / 手机一键 Root 成功后，应用此入口刷徽标。
     */
    suspend fun probeWithPrivilege(context: Context): Snapshot {
        val local = probe()
        if (local.any) return local
        if (tempSuOkViaPrivilege(context)) {
            return Snapshot(temporary = true, permanent = false)
        }
        return local
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

    private suspend fun tempSuOkViaPrivilege(context: Context): Boolean {
        val commands = listOf(
            TempRootShellCommands.VERIFY_SU_TMP,
            TempRootShellCommands.VERIFY_SU_APEX,
        )
        for (cmd in commands) {
            val output = if (ChannelLine.usesEmbeddedBridge) {
                OneKukuEmbeddedAdbActivator.execWhitelistedShell(
                    context,
                    cmd,
                    12_000L,
                ).let { if (it.ok) it.output else "" }
            } else {
                ShizukuTempRootShell.execWhitelistedShell(cmd, 12_000L).output
            }
            if (TempRootShellCommands.looksLikeRootSuccess(output)) return true
        }
        return false
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
