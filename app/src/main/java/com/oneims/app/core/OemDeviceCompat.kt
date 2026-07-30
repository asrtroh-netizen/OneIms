package com.oneims.app.core

import android.os.Build

/**
 * OEM 兼容策略入口：只做识别与策略开关，不硬拦业务写入。
 *
 * 产品优先级：**第一 Pixel**（通信 + 开机自启硬保证）→ **第二** 其它机子 VoWIFI 容错
 * （vivo / OPPO / 一加 / 小米 / 三星 / 荣耀等）。Google/Pixel 永不进入国产 soft 门控。
 * （见 docs/architecture/2026-07-30-product-priority-pixel-first.md）。
 */
object OemDeviceCompat {

    fun manufacturer(): String = Build.MANUFACTURER.orEmpty()

    fun brand(): String = Build.BRAND.orEmpty()

    /** 小米系（含 Redmi / POCO / HyperOS 常见 brand）。 */
    fun isXiaomiFamily(
        manufacturer: String = manufacturer(),
        brand: String = brand(),
    ): Boolean {
        val tokens = identityTokens(manufacturer, brand)
        return tokens.any { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") } ||
            hasMiuiOrHyperOsMarker()
    }

    /**
     * 国产 VoWIFI 导向 OEM：同一套 PixelIMS 式容错门控。
     * 不含 Google/Pixel；通信主链路仍仅服务 Pixel。
     */
    fun isDomesticVowifiOem(
        manufacturer: String = manufacturer(),
        brand: String = brand(),
    ): Boolean {
        if (isGooglePixelFamily(manufacturer, brand)) return false
        if (isXiaomiFamily(manufacturer, brand)) return true
        val tokens = identityTokens(manufacturer, brand)
        val markers = listOf(
            "vivo", "iqoo",
            "oppo", "realme", "oneplus",
            // 通信本身正常、主要只要 VoWIFI 的 OEM（含三星 / 荣耀）
            "samsung",
            "meizu", "honor", "huawei", "hihonor",
        )
        return markers.any { marker -> tokens.any { it.contains(marker) } }
    }

    fun isGooglePixelFamily(
        manufacturer: String = manufacturer(),
        brand: String = brand(),
    ): Boolean {
        val tokens = identityTokens(manufacturer, brand)
        return tokens.any { it.contains("google") || it == "pixel" || it.contains("pixel") }
    }

    /**
     * 回读验证是否应软化：写已接受但 telephony 侧延迟/过滤导致 5s 内对不上时，
     * 硬抛只会放大闪退面；改为记日志并由 Writer 按 key 验真。
     * 仅国产 VoWIFI OEM；Pixel 仍硬验真。
     */
    fun softenCarrierConfigReadback(): Boolean = isDomesticVowifiOem()

    fun summaryLine(): String = buildString {
        append(manufacturer())
        append('/')
        append(brand())
        when {
            isGooglePixelFamily() -> append(" · pixel-primary")
            isDomesticVowifiOem() -> append(" · domestic-vowifi-oem")
        }
        if (isXiaomiFamily()) append(" · xiaomi-family")
        val marker = miuiVersionMarker()
        if (!marker.isNullOrBlank()) append(" · ").append(marker)
    }

    private fun identityTokens(manufacturer: String, brand: String): List<String> =
        listOf(manufacturer, brand).map { it.lowercase().trim() }.filter { it.isNotEmpty() }

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
