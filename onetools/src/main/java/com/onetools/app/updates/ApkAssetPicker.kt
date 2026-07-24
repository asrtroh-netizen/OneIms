package com.onetools.app.updates

/**
 * Prefer APKs matching device ABI, then user prefer tokens, then generic `.apk`.
 * Beats manual-only filters when users forget ABI rules.
 */
object ApkAssetPicker {
    fun pick(
        candidates: List<ReleaseAsset>,
        prefer: List<String>,
        abis: List<String>,
    ): ReleaseAsset {
        require(candidates.isNotEmpty()) { "No APK candidates" }
        return candidates.maxBy { score(it.name, prefer, abis) }
    }

    fun score(name: String, prefer: List<String>, abis: List<String>): Int {
        var s = 0
        val lower = name.lowercase()
        // Wrong explicit ABIs get punished hard.
        val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")
        val mentionsAbi = knownAbis.any { lower.contains(it) }
        if (mentionsAbi) {
            val matched = abis.any { lower.contains(it.lowercase()) }
            s += if (matched) 1_000 else -500
            // Prefer primary ABI more.
            abis.firstOrNull()?.let { primary ->
                if (lower.contains(primary.lowercase())) s += 200
            }
        } else {
            // Universal / no-ABI APK: acceptable fallback.
            s += 100
        }
        prefer.forEachIndexed { index, token ->
            if (token.isNotBlank() && lower.contains(token.lowercase())) {
                s += 80 - index * 5
            }
        }
        if (lower.contains("debug")) s -= 50
        if (lower.contains("unsigned")) s -= 40
        return s
    }
}
