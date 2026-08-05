package com.oneims.app.core

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * 临时 Root so 本地目录：按 [Build.DEVICE] + [Build.ID] 选 APK assets。
 * 远端补齐见 [TempRootSoProvider]（OneSo-assets）。
 */
object TempRootSoCatalog {
    data class Match(
        val device: String,
        val buildId: String,
        val assetPath: String,
    )

    fun currentDevice(): String = Build.DEVICE.orEmpty()

    fun currentBuildId(): String = Build.ID.orEmpty()

    fun resolve(context: Context): Match? {
        val device = currentDevice()
        val buildId = currentBuildId()
        if (device.isBlank() || buildId.isBlank()) return null
        return runCatching {
            val json = context.assets.open("temproot/catalog.json")
                .bufferedReader()
                .use { it.readText() }
            val root = JSONObject(json)
            val devices = root.optJSONObject("devices") ?: return null
            val builds = devices.optJSONObject(device) ?: return null
            val fileName = builds.optString(buildId, "").trim()
            if (fileName.isEmpty()) return null
            val assetPath =
                if (fileName.startsWith("temproot/")) fileName
                else "temproot/${fileName.substringAfterLast('/')}"
            Match(
                device = device,
                buildId = buildId,
                assetPath = assetPath,
            )
        }.getOrNull()
    }

    /**
     * 本地 catalog 若写成 `so/<BuildId>/preload-….so`，可供远端 URL 拼接提示。
     * 仅文件名时返回 null（避免瞎猜目录）。
     */
    fun resolveRemoteHint(context: Context, device: String, buildId: String): String? {
        return runCatching {
            val json = context.assets.open("temproot/catalog.json")
                .bufferedReader()
                .use { it.readText() }
            val root = JSONObject(json)
            val devices = root.optJSONObject("devices") ?: return null
            val builds = devices.optJSONObject(device) ?: return null
            val raw = builds.optString(buildId, "").trim()
            when {
                raw.startsWith("so/") -> raw
                raw.startsWith("https://") -> raw
                else -> null
            }
        }.getOrNull()
    }

    fun isCurrentDeviceSupported(context: Context): Boolean =
        TempRootSoProvider.isLikelySupported(context)
}
