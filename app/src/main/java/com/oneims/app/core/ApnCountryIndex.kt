package com.oneims.app.core

import java.util.Locale

data class ApnCountryInfo(
    val iso: String,
    val nameZh: String,
    val nameEn: String,
    val mccPrefixes: List<String>,
)

/**
 * 离线 APN 国家索引：把 ISO 国家码映射到中英文名与常见 MCC，供搜索匹配。
 */
object ApnCountryIndex {
    private val mccPrefixesByIso = mapOf(
        "CN" to listOf("460"),
        "HK" to listOf("454"),
        "MO" to listOf("455"),
        "TW" to listOf("466"),
        "NZ" to listOf("530"),
        "GB" to listOf("234", "235"),
        "US" to listOf("310", "311", "312", "313", "314", "315", "316"),
        "JP" to listOf("440", "441"),
        "KR" to listOf("450"),
        "SG" to listOf("525"),
        "AU" to listOf("505"),
        "CA" to listOf("302"),
        "DE" to listOf("262"),
        "FR" to listOf("208"),
    )
    private val isoAliases = mapOf("UK" to "GB")
    private val conciseChineseNames = mapOf(
        "HK" to "香港",
        "MO" to "澳门",
        "TW" to "台湾",
    )
    private val aliasesByIso = isoAliases.entries
        .groupBy(keySelector = { it.value }, valueTransform = { it.key })
    private val countries = Locale.getISOCountries().map { iso ->
        val locale = Locale.Builder().setRegion(iso).build()
        ApnCountryInfo(
            iso = iso,
            nameZh = conciseChineseNames[iso]
                ?: locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE),
            nameEn = locale.getDisplayCountry(Locale.ENGLISH),
            mccPrefixes = mccPrefixesByIso[iso].orEmpty(),
        )
    }

    private val isoByMccPrefix = countries
        .flatMap { info -> info.mccPrefixes.map { prefix -> prefix to info.iso } }
        .toMap()

    fun resolveSearchTokens(query: String): Set<String> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return emptySet()
        val tokens = mutableSetOf(normalized)
        isoAliases[normalized.uppercase(Locale.ROOT)]?.let { canonicalIso ->
            tokens += canonicalIso.lowercase(Locale.ROOT)
        }
        countries.forEach { info ->
            val aliases = aliasesByIso[info.iso].orEmpty()
            val matches = normalized == info.iso.lowercase(Locale.ROOT) ||
                aliases.any { alias -> normalized == alias.lowercase(Locale.ROOT) } ||
                info.nameZh.contains(query.trim()) ||
                info.nameEn.lowercase(Locale.ROOT).contains(normalized) ||
                info.mccPrefixes.any { mcc -> mcc.startsWith(normalized) || normalized == mcc }
            if (matches) {
                tokens += info.iso.lowercase(Locale.ROOT)
                aliases.forEach { alias -> tokens += alias.lowercase(Locale.ROOT) }
                tokens += info.nameZh
                tokens += info.nameEn.lowercase(Locale.ROOT)
                info.mccPrefixes.forEach { mcc ->
                    tokens += mcc
                    tokens += mcc.take(3)
                }
            }
        }
        if (normalized.length == 3 && normalized.all { it.isDigit() }) {
            isoByMccPrefix[normalized]?.let { iso ->
                tokens += iso.lowercase(Locale.ROOT)
            }
        }
        return tokens
    }

    fun displayName(iso: String): String {
        val canonicalIso = isoAliases[iso.uppercase(Locale.ROOT)] ?: iso
        val info = countries.firstOrNull { it.iso.equals(canonicalIso, ignoreCase = true) }
        return info?.nameZh ?: iso
    }

    fun englishName(iso: String): String {
        val canonicalIso = isoAliases[iso.uppercase(Locale.ROOT)] ?: iso
        val info = countries.firstOrNull { it.iso.equals(canonicalIso, ignoreCase = true) }
        return info?.nameEn ?: iso
    }
}
