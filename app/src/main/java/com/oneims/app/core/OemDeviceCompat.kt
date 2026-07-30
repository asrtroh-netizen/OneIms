package com.oneims.app.core

import android.os.Build

/**
 * OEM 兼容策略入口：只做识别与策略开关，不硬拦业务写入。
 *
 * 小米 / Redmi / POCO / HyperOS 对 Instrumentation 委托、CarrierConfig 回读、
 * IMS provisioning 更苛刻；PixelIMS 表面正常往往只因写集更小、异常多被吞。
 */
object OemDeviceCompat {

    fun manufacturer(): String = Build.MANUFACTURER.orEmpty()

    fun brand(): String = Build.BRAND.orEmpty()

    /** 小米系（含 Redmi / POCO / HyperOS 常见 brand）。 */
    fun isXiaomiFamily(
        manufacturer: String = manufacturer(),
        brand: String = brand(),
    ): Boolean {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        return m.contains("xiaomi") ||
            m.contains("redmi") ||
            b.contains("xiaomi") ||
            b.contains("redmi") ||
            b.contains("poco") ||
            hasMiuiOrHyperOsMarker()
    }

    /**
     * 回读验证是否应软化：写已接受但 telephony 侧延迟/过滤导致 5s 内对不上时，
     * 硬抛只会放大闪退面；改为记日志并由 Writer 按 key 验真。
     * provisioning invoke 软化仅由 [ProvisioningWritePolicy.isSoftProvisioningIntKey] 白名单控制，
     * 绝不对 VoLTE key=10 等硬键吞异常。
     */
    fun softenCarrierConfigReadback(): Boolean = isXiaomiFamily()

    fun summaryLine(): String = buildString {
        append(manufacturer())
        append('/')
        append(brand())
        if (isXiaomiFamily()) append(" · xiaomi-family")
        val marker = miuiVersionMarker()
        if (!marker.isNullOrBlank()) append(" · ").append(marker)
    }

    private fun hasMiuiOrHyperOsMarker(): Boolean {
        val marker = miuiVersionMarker()?.lowercase().orEmpty()
        return marker.contains("miui") || marker.contains("hyper")
    }

    private fun miuiVersionMarker(): String? {
        val keys = listOf(
            "ro.miui.ui.version.name",
            "ro.mi.os.version.name",
            "ro.mi.os.version.incremental",
        )
        for (key in keys) {
            val value = systemProperty(key)
            if (!value.isNullOrBlank()) return "$key=$value"
        }
        return null
    }

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
