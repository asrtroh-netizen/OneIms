package com.onetools.app.live

/**
 * 第一批国内实时状态源（美团 / 滴滴 / 菜鸟）。
 * 包名白名单可随厂商分包扩展，解析侧只认这些 UID/包。
 */
enum class LiveStatusSource(
    val id: String,
    val chipPrefix: String,
    val packages: Set<String>,
) {
    MEITUAN(
        id = "meituan",
        chipPrefix = "美",
        packages = setOf(
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
        ),
    ),
    DIDI(
        id = "didi",
        chipPrefix = "滴",
        packages = setOf(
            "com.sdu.didi.psnger",
        ),
    ),
    CAINIAO(
        id = "cainiao",
        chipPrefix = "菜",
        packages = setOf(
            "com.cainiao.wireless",
        ),
    );

    companion object {
        fun fromPackage(packageName: String?): LiveStatusSource? {
            if (packageName.isNullOrBlank()) return null
            return entries.firstOrNull { packageName in it.packages }
        }

        fun fromId(id: String?): LiveStatusSource? =
            entries.firstOrNull { it.id == id }
    }
}
