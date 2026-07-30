# 2026-07-30 · OneKuku MINI（V15.1.0 缩减独立包）

**仓**：`E:\GQ\One\_forks\ShizukuDropIn-Local`  
**Flavor**：`onekukuMini`  
**包名**：`com.oneims.onekuku.core` · `versionName=OneKuku-MINI` · `versionCode=151010`

## 目标

把 V15.1.0 DropIn 收成 **更 MINI 的 OneKuku 独立通道包**，为撤内嵌 OneBridge 铺路。  
本阶段：**软裁剪 + 身份落地 + 可编包**；Plus 类仍在 APK 内但运行时闸死。

## 已改

| 面 | 改动 |
|---|---|
| Gradle | `onekukuMini` flavor；`BuildConfig.ONEKUKU_MINI` |
| Server | `ONEKUKU_MINI_APPLICATION_ID`；manager 解析 / isManager |
| starter.cpp | `pm path` 第三候选 |
| Settings | Plus getter / sync 全关（保留 Force WADB） |
| UI | `src/onekukuMini/res/xml/settings_main.xml` 去掉 Feature Hub |

## 构建

```bat
cd /d E:\GQ\One\_forks\ShizukuDropIn-Local
gradlew :manager:assembleOnekukuMiniRelease
```

## 与 OneIMS 衔接（未做）

1. `onekuku` 线探测 `com.oneims.onekuku.core` 并走 Shizuku 客户端契约  
2. 撤 `:bridge` / 内嵌 OneBridge  
3. 物理删 Plus 模块减体积（见 `2026-07-30-v1510-strip-candidates.md`）

## 注意

- **不是** stock `moe.shizuku.privileged.api`，可与官方/DropIn 同机并存（不同 applicationId）  
- 第三方默认 Shizuku API 仍指向官方包名；OneIMS 需显式绑 MINI 包  
