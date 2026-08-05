package com.oneims.app.core

import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * 临时 Root so 目录：按 [Build.DEVICE] + [Build.ID] 选资产。
 * 首发仅收录 comet / CP2A.260705.006。
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
            Match(
                device = device,
                buildId = buildId,
                assetPath = "temproot/$fileName",
            )
        }.getOrNull()
    }

    fun isCurrentDeviceSupported(context: Context): Boolean = resolve(context) != null
}
