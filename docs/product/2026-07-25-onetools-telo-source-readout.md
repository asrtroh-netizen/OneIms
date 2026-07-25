# 2026-07-25 · Pixel Telo 源码对照（归属地 / 拨号器）

> 仓库：`https://github.com/Pixel-Tailor-CN/PixelTelo`（Apache-2.0）  
> 方式：GitHub API / raw 只读；**不合并源码进 OneTools**（架构 Out）。

## 1. 系统集成两件套（与 README 一致）

| 组件 | 路径 | 作用 |
|---|---|---|
| `TeloDirectoryProvider` | `…/provider/TeloDirectoryProvider.kt` | Contacts Directory → 系统拨号 `phone_lookup` |
| `TeloCallScreeningService` | `…/service/TeloCallScreeningService.kt` | 默认来电筛选：拦 / 仅提示 / 重复来电策略 |

## 2. 关键发现：Directory **不是**「任意号码画归属地」

`TeloDirectoryProvider.queryPhoneNumber` 逻辑实质：

1. `spamNumberRepository.checkSpam(number)`
2. **仅当 `shouldBlock == true`** 才往 Cursor 塞 `DISPLAY_NAME = spamLabel`
3. **非骚扰 → 直接空 Cursor**（拨号器拿不到任何 Directory 行）

也就是说：Telo 写进**系统拨号器**的，主要是 **骚扰标签**，不是「每个号都显示省市运营商」。

## 3. 归属地（省市）实际从哪来？

| 路径 | 证据 | 是否「系统拨号原生」 |
|---|---|---|
| **联网查询** `QueryApi` → `PhoneLocationInfo(province, city, cardType)` | `QueryApi.kt` / `SpamNumberRepository.buildNetworkResult` | 否（数据在查询结果里） |
| **来电悬浮窗** `IncomingCallOverlay` + `IncomingCallOverlayFormatter` | `showLocationOverlayIfNeeded`；设置项 `KEY_SHOW_LOCATION_OVERLAY` | **否**（App 悬浮层，不是 Phone 内嵌） |
| 离线库 `MastDatabase` / `SpamNumberDao` | 骚扰号 + tag，不是通用号段归属地表 | 用于拦截，不是全号段 geo |

悬浮窗文案格式：`province + " " + city`（超时/未知有单独 string）。

## 4. 查号流水线（简化）

```
白名单 → 黑名单 → 本地骚扰库(Room/mast) →（可选）联网 QueryApi
         ↓
CheckResult(shouldBlock, label, locationInfo, …)
         ↓
CallScreening 决策 +（可选）Overlay 显示 locationInfo
         ↓
Directory 仅在 shouldBlock 时把 label 喂给拨号器
```

## 5. 对 OneCaller 的含义（可落地、不抄代码）

> **用户拍板：不要看来电悬浮。** 归属地验收只认 Directory → 系统 Phone 原生行。

| Telo | OneCaller 现状 / 建议 |
|---|---|
| Directory 只喂骚扰标签 | 已把 **geo + LABEL/拦截** 合成进 Directory——**主战场** |
| 归属地靠联网 + **悬浮窗** | **悬浮窗路线 Out**；只扩本地/CDN `geo_v1` 喂 Directory |
| 离线库是骚扰库增量更新 | **已落地干净室 `onespam.db` 全路数**（清单/SHA-256/本地命中/可选联网）；号段归属另走 MIT `geo.dat`；禁止 GPL `phone.dat` |
| Apache-2.0 可学习契约 | **禁止整段移植**；只复用 Directory / CallScreening 公开契约 |

## 6. 若用户体感「Telo 拨号里有归属地」

更可能是：

1. 把 **来电悬浮窗** 当成了系统拨号原生行；或  
2. 骚扰标签文案里带了地区感；或  
3. 某版本行为与当前 HEAD 不一致  

以当前 HEAD 源码为准：**纯归属地 ≠ Directory 主路径**。
