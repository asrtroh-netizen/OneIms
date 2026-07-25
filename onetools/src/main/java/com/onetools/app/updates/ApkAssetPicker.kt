package com.onetools.app.updates

/**
 * Prefer APKs matching device ABI, then user prefer tokens, then generic `.apk`.
 * Optional [apkRegex] filters candidates by file name before ABI scoring.
 */
object ApkAssetPicker {
    fun pick(
        candidates: List<ReleaseAsset>,
        prefer: List<String>,
        abis: List<String>,
        apkRegex: String? = null,
    ): ReleaseAsset {
        val filtered = filterByRegex(candidates, apkRegex)
        require(filtered.isNotEmpty()) {
            if (apkRegex.isNullOrBlank()) "No APK candidates"
            else "No APK matches regex: $apkRegex"
        }
        return filtered.maxBy { score(it.name, prefer, abis) }
    }

    fun filterByRegex(candidates: List<ReleaseAsset>, apkRegex: String?): List<ReleaseAsset> {
        val pattern = apkRegex?.trim().orEmpty()
        if (pattern.isEmpty()) return candidates
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
            ?: return candidates
        return candidates.filter { regex.containsMatchIn(it.name) }
    }

    fun score(name: String, prefer: List<String>, abis: List<String>): Int {
        var s = 0
        val lower = name.lowercase()
        val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "armeabi")
        val mentionsAbi = knownAbis.any { lower.contains(it) }
        if (mentionsAbi) {
            val matched = abis.any { lower.contains(it.lowercase()) }
            s += if (matched) 1_000 else -500
            abis.firstOrNull()?.let { primary ->
                if (lower.contains(primary.lowercase())) s += 200
            }
        } else {
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
