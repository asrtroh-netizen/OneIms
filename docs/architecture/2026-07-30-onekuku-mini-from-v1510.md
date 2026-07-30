# 2026-07-30 · OneKuku Care / MINI（V15.1.0 缩减独立包）

**仓**：`E:\GQ\One\_forks\ShizukuDropIn-Local`  
**Flavor**：`onekukuMini`  
**包名**：`com.onekuku.care` · `versionName=OneKuku-MINI` · `versionCode=151010`

## 目标

把 V15.1.0 DropIn 收成 **OneIMS 专用迷你通道包**，为撤内嵌 OneBridge 铺路。

## 已砍（onekukuMini）

| 项 | 做法 |
|---|---|
| 自动化卡 + automation | Settings 硬关；Manifest remove AutomationService / AICore+ / Locale 插件 |
| Samsung UID1000 | Settings getter MINI 恒 false |
| 终端 / 活动日志 / 了解更多 | Settings 硬关；首页去入口；Manifest remove 对应 Activity |

## 存疑未砍（解释见交付）

- Service Doctor（诊断页）
- Root Compatibility Hub（Root 适配仪表盘）

## 构建

```bat
cd /d E:\GQ\One\_forks\ShizukuDropIn-Local
gradlew :manager:assembleOnekukuMiniRelease
```

## 与 OneIMS 衔接（未做）

1. `onekuku` 线探测 `com.onekuku.care` 并走 Shizuku 客户端契约  
2. 撤 `:bridge` / 内嵌 OneBridge  
3. 物理删 Plus 模块减体积（见 `2026-07-30-v1510-strip-candidates.md`）

## 注意

- **不是** stock `moe.shizuku.privileged.api`，可与官方/DropIn 同机并存  
- 第三方默认 Shizuku API 仍指向官方包名；OneIMS 需显式绑 Care 包  
