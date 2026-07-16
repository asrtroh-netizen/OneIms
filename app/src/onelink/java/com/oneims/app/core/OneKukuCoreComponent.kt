package com.oneims.app.core

import android.content.Context

/**
 * OneLink 桩：不内嵌 OneBridge。供 shared 代码（如 [ShizukuSetupHelper]）编译通过。
 * 通道安装态改为探测官方 Shizuku 包。
 */
object OneKukuCoreComponent {
    const val HOST_PACKAGE: String = "com.oneims.onelink"

    @Deprecated("Phase4 embedded into host app", ReplaceWith("HOST_PACKAGE"))
    const val BRIDGE_PACKAGE: String = HOST_PACKAGE

    const val CORE_PACKAGE: String = HOST_PACKAGE

    val CANDIDATE_PACKAGES: List<String> = listOf(HOST_PACKAGE)

    const val BUNDLED_BRIDGE_ASSET_NAME: String = "oneims-bridge.apk"
    const val BUNDLED_ASSET_NAME: String = BUNDLED_BRIDGE_ASSET_NAME
    val BUNDLED_ASSET_CANDIDATES: List<String> = emptyList()

    const val SHELL_BOOT_OK: String = "__OB_BOOT_OK__"
    const val SHELL_BOOT_MISS: String = "__OB_BOOT_MISS__"

    enum class Status {
        MISSING,
        INSTALLED_STOPPED,
        RUNNING_NEED_AUTH,
        READY,
    }

    enum class PrepareResult {
        OPENED_ADB_GUIDE,
        INSTALLING_BUNDLED,
        NEEDS_DOWNLOAD,
        DOWNLOADING_CORE,
        FAILED,
    }

    fun resolveStatus(context: Context): Status =
        if (isInstalled(context)) Status.INSTALLED_STOPPED else Status.MISSING

    fun resolveCorePackage(context: Context): String? =
        if (isInstalled(context)) "moe.shizuku.privileged.api" else null

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        }.getOrDefault(false)

    fun adbStartCommand(context: Context? = null): String = ""

    fun bridgeBootShellCommand(
        packageName: String = HOST_PACKAGE,
        forceRestart: Boolean = false,
    ): String = ""

    fun guidedActivationScript(context: Context): String = ""

    fun prepare(context: Context): PrepareResult = PrepareResult.FAILED
}
