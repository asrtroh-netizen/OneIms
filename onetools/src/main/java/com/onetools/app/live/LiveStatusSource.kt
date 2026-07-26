package com.onetools.app.live

/**
 * 国内实时状态源白名单（海报兼容集 + 美团/滴滴/菜鸟）。
 */
enum class LiveStatusSource(
    val id: String,
    val chipPrefix: String,
    /** UI 展示名（不走 strings 也能列全量开关）。 */
    val labelZh: String,
    val packages: Set<String>,
) {
    MEITUAN(
        id = "meituan",
        chipPrefix = "美",
        labelZh = "美团",
        packages = setOf(
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
        ),
    ),
    ELEME(
        id = "eleme",
        chipPrefix = "饿",
        labelZh = "饿了么",
        packages = setOf(
            "me.ele",
            "me.ele.android",
        ),
    ),
    DIDI(
        id = "didi",
        chipPrefix = "滴",
        labelZh = "滴滴",
        packages = setOf(
            "com.sdu.didi.psnger",
        ),
    ),
    CAINIAO(
        id = "cainiao",
        chipPrefix = "菜",
        labelZh = "菜鸟",
        packages = setOf(
            "com.cainiao.wireless",
        ),
    ),
    AMAP(
        id = "amap",
        chipPrefix = "高",
        labelZh = "高德地图",
        packages = setOf(
            "com.autonavi.minimap",
        ),
    ),
    BAIDU_MAP(
        id = "baidu_map",
        chipPrefix = "百",
        labelZh = "百度地图",
        packages = setOf(
            "com.baidu.BaiduMap",
        ),
    ),
    QQ_MUSIC(
        id = "qq_music",
        chipPrefix = "Q",
        labelZh = "QQ音乐",
        packages = setOf(
            "com.tencent.qqmusic",
        ),
    ),
    NETEASE_MUSIC(
        id = "netease_music",
        chipPrefix = "云",
        labelZh = "网易云音乐",
        packages = setOf(
            "com.netease.cloudmusic",
        ),
    ),
    RAIL12306(
        id = "rail12306",
        chipPrefix = "火",
        labelZh = "铁路12306",
        packages = setOf(
            "com.MobileTicket",
        ),
    ),
    UMETRIP(
        id = "umetrip",
        chipPrefix = "航",
        labelZh = "航旅纵横",
        packages = setOf(
            "com.umetrip.android.msky.app",
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
