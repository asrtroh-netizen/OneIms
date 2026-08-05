# 首页 ROOT 徽标 + 临时 Root 持久化改运营商

**日期**：2026-08-05  
**规模**：M  
**状态**：已落地（代码 + 单测）

## 变更摘要

1. **状态卡**：移除与底部重复的「设备详情」入口；右上角增加黑金 `ROOT` 小标签（临时或永久 Root 探测到才显示）。
2. **开关行**：在「Root 开机自启」「持久性 VoLTE/NR」下增加第三行「临时 Root 持久化改运营商」，样式一致；**有 Root（临时或永久）即显示**（产品确认：不是「仅永久 Root」）。
3. **融合教程**：开关开启后（及 CarrierConfig 写入装饰路径）会按教程最小键集合补写  
   `/data/user_de/0/com.android.phone/files/carrierconfig-*.xml`（只改已存在文件 + `chown radio`）。

## 关键落点

| 能力 | 文件 |
|---|---|
| Root 探测 | `RootPresenceProbe.kt` |
| XML 最小补丁 | `CarrierConfigXmlMinimalPatcher.kt` |
| su 写入 | `TempRootCarrierXmlPersist.kt` |
| Writer 旁路挂钩 | `RootPersistenceSupport.decorateResultMessage` |
| UI | `StatusHero` / `HomeScreen` / `MainActivity` / strings |

## 偏好同源

首页第三行开关与实验页「Root 持久化增强」共用 `ConfigStore.isRootPersistEnhance`。

## 验证

- `:app:compileOnekukuDebugKotlin`
- `:app:testOnekukuDebugUnitTest --tests com.oneims.app.core.CarrierConfigXmlMinimalPatcherTest`
