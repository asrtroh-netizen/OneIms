# OneTools 0.7.0 · 国内适配器扩容

新增源与适配器（海报兼容集）：

| 源 | 包名 | 适配器 |
|---|---|---|
| 饿了么 | `me.ele` | ElemeVendorAdapter |
| 高德地图 | `com.autonavi.minimap` | NavVendorAdapter |
| 百度地图 | `com.baidu.BaiduMap` | NavVendorAdapter |
| QQ音乐 | `com.tencent.qqmusic` | MusicVendorAdapter |
| 网易云音乐 | `com.netease.cloudmusic` | MusicVendorAdapter |
| 铁路12306 | `com.MobileTicket` | Rail12306VendorAdapter |
| 航旅纵横 | `com.umetrip.android.msky.app` | UmetripVendorAdapter |

既有：美团 / 滴滴 / 菜鸟。Live Lab 来源开关改为遍历全量 enum；Manifest `<queries>` 已补齐。
