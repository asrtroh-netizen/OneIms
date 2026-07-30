package com.oneims.app.core

/** 运营商短名格式化，供应用内卡片展示（原挂在已删除的切卡模块上）。 */
fun formatCarrierShortName(name: String?): String {
    val raw = name?.trim().orEmpty()
    if (raw.isEmpty()) return "—"

    return when {
        raw.contains("China Mobile", ignoreCase = true) -> "CMCC"
        raw.contains("中国移动") -> "CMCC"
        raw.contains("China Unicom", ignoreCase = true) -> "CU"
        raw.contains("中国联通") -> "CU"
        raw.contains("China Telecom", ignoreCase = true) -> "CT"
        raw.contains("中国电信") -> "CT"
        raw.contains("CMHK", ignoreCase = true) -> "CMHK"
        raw.length > 8 -> raw.take(8)
        else -> raw
    }
}
