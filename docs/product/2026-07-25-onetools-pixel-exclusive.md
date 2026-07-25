# 2026-07-25 · OneTools 产品边界：Pixel 专属

## 拍板

用户明确：**做的是 Pixel 专属工具，不管其他厂商。**

## 含义

| 做 | 不做 |
|---|---|
| Google Pixel / 类原生 Phone 上验收 Caller（归属 + 拦截） | 小米/华为/OPPO/vivo 拨号器兼容表、分 ROM 分支 |
| CallScreening + Contacts Directory（AOSP 契约） | 厂商私有查号 SDK / 云库对接 |
| Meter 对标 Pixel Meter + Pixel/AOSP 状态栏观感（贴顶/芯片） | 「各家 OEM SystemUI」专项适配工程 |

## Caller「能否像 OEM」在本边界下的答法

这里的「像」= **像 Pixel 系统电话那套体验**（默认来电筛选角色 + 来电标签），不是像国产厂商全家桶云标记。  
Pixel 上 Directory + CallScreening 是正道；其它 ROM 显示好坏 **不纳入成功标准**。

## 归属地展示硬边界（2026-07-25 用户拍板）

| 算成功 | 不算 / 不做 |
|---|---|
| **系统拨号器 / Phone 来电界面** 里由 Directory 画出的原生归属地行 | **来电悬浮窗**（Telo Overlay 路线）——明确 **不要看、不做为验收** |
| CallScreening 拦截行为 | 用悬浮窗冒充「原生归属地」 |

对照 Telo：其 Directory 当前 HEAD 主要喂骚扰标签；归属地多靠联网+悬浮。OneCaller **只卷 Directory 原生行**，用自有 `geo_v1` / CDN 把号段喂满。

## 查号成本硬边界（2026-07-25 用户拍板 · 最省钱）

| 走 | 不走 |
|---|---|
| MIT `geo.dat` 本地归属 | 采购百度 SPNS / 聚合 / 阿里云查号（暂缓） |
| OneBlock + `onespam` 本地骚扰（精确/前缀） | 依赖 Telo 或商业云库当主路径 |
| 默认 **仅离线查号**（`CallerPrefs.noNetworkQuery=true`） | 默认联网扣费查询 |
| `ONE_CALLER_QUERY_URL` 保持空 | 把 Key 写进 APK |

以后若要联网，再显式关「仅离线」并自建网关；报价调研见 `2026-07-25-onetools-caller-lookup-api-pricing.md`。

## 文档指针

- 架构：`docs/architecture/2026-07-25-onetools-architecture-blueprint.md`
- 变更：本文件 + Caller/Meter 相关 changes
