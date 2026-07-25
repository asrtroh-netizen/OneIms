package com.onetools.app.battery

import android.os.Build

/**
 * Pixel-class design capacity presets (typical mAh from public Google/Wikipedia specs).
 * Matching prefers longer model names first (e.g. "Pixel 9 Pro XL" before "Pixel 9").
 */
object PixelDesignCapacity {
    data class Preset(
        val label: String,
        val mah: Int,
        /** Substrings matched against [Build.MODEL] (case-insensitive). */
        val modelKeys: List<String>,
    )

    val PRESETS: List<Preset> = listOf(
        Preset("Pixel 10 Pro XL", 5200, listOf("Pixel 10 Pro XL")),
        Preset("Pixel 10 Pro Fold", 5015, listOf("Pixel 10 Pro Fold")),
        Preset("Pixel 10 Pro", 4870, listOf("Pixel 10 Pro")),
        Preset("Pixel 10a", 5100, listOf("Pixel 10a")),
        Preset("Pixel 10", 4970, listOf("Pixel 10")),
        Preset("Pixel 9 Pro Fold", 4650, listOf("Pixel 9 Pro Fold")),
        Preset("Pixel 9 Pro XL", 5060, listOf("Pixel 9 Pro XL")),
        Preset("Pixel 9 Pro", 4700, listOf("Pixel 9 Pro")),
        Preset("Pixel 9a", 5100, listOf("Pixel 9a")),
        Preset("Pixel 9", 4700, listOf("Pixel 9")),
        Preset("Pixel 8 Pro", 5050, listOf("Pixel 8 Pro")),
        Preset("Pixel 8a", 4492, listOf("Pixel 8a")),
        Preset("Pixel 8", 4575, listOf("Pixel 8")),
        Preset("Pixel Fold", 4821, listOf("Pixel Fold")),
        Preset("Pixel 7 Pro", 5000, listOf("Pixel 7 Pro")),
        Preset("Pixel 7a", 4385, listOf("Pixel 7a")),
        Preset("Pixel 7", 4355, listOf("Pixel 7")),
        Preset("Pixel 6 Pro", 5003, listOf("Pixel 6 Pro")),
        Preset("Pixel 6a", 4410, listOf("Pixel 6a")),
        Preset("Pixel 6", 4614, listOf("Pixel 6")),
        Preset("Pixel 5a", 4680, listOf("Pixel 5a")),
        Preset("Pixel 5", 4080, listOf("Pixel 5")),
        Preset("Pixel 4a (5G)", 3885, listOf("Pixel 4a (5G)", "Pixel 4a 5G")),
        Preset("Pixel 4a", 3140, listOf("Pixel 4a")),
        Preset("Pixel 4 XL", 3700, listOf("Pixel 4 XL")),
        Preset("Pixel 4", 2800, listOf("Pixel 4")),
    )

    fun match(
        model: String = Build.MODEL,
        manufacturer: String = Build.MANUFACTURER,
    ): Preset? {
        val m = model.trim()
        if (m.isEmpty()) return null
        val isGoogle = manufacturer.equals("Google", ignoreCase = true) ||
            m.contains("Pixel", ignoreCase = true)
        if (!isGoogle) return null
        return PRESETS.firstOrNull { preset ->
            preset.modelKeys.any { key -> m.contains(key, ignoreCase = true) }
        }
    }

    fun presetForMah(mah: Int): Preset? = PRESETS.firstOrNull { it.mah == mah }
}
