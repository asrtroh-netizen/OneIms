# 2026-07-18 · HSSkyBoy/Shizuku 底 + 库内（thedjchi V15）皮

工作目录：`E:\GQ\One\_forks\HSSkyBoy-Shizuku`（独立于 OneIMS 主仓；**不改** `thedjchi-Shizuku` 现有自启补丁）

## 决策

| 项 | 选择 | 原因 |
|---|---|---|
| 自启逻辑 | **原样保留 HSSkyBoy** | 现方案（thedjchi 对齐/补丁）仍有问题，停止继续改 |
| UI/品牌 | **库内皮 = thedjchi V15** | 三态/两态 Hero + 2×2 快捷格 + `#0B57D0`；显示名 `Shizuku` |
| 包名 | 上游 `moe.shizuku.privileged.api` | 保持 Shizuku API 契约；与官版 Shizuku 互斥安装 |

## 换皮清单

- `app_name` → `Shizuku`（对齐库内皮，非 OneIms Lite 字样）
- Hero 色：`hero_inactive_*` / `hero_ready_*`（来自 thedjchi）
- Compose 首页：`LibrarySkinHome.kt`（Hero + 无线启动卡 + 2×2 Apps/Terminal/Root/PC ADB）
- 主色 `#0B57D0` / `#A9C7FF`；卡片 20dp
- APK 输出名：`OneIms-Lite-HSSkyBoy-v{version}-{variant}.apk`（文件名历史保留）

## 自启链路（未改逻辑）

`BOOT` → `WirelessBootStartWorker`（UNMETERED）→ `SelfStarterService`（mDNS + ADB）+ `USER_PRESENT` 解锁续跑

## 本机构建适配（不改正自启逻辑）

| 项 | 上游默认 | 本机 |
|---|---|---|
| compileSdk / targetSdk | 37 | 36 |
| buildTools | 37.0.0 | 35.0.0 |
| ndkVersion | 29.x | 27.0.12077973 |
| cmake | 3.31.0+ | 3.22.1 |

## 产物

- `E:\GQ\One\_forks\HSSkyBoy-Shizuku\out\apk\OneIms-Lite-HSSkyBoy-v13.6.1-RC2.r1.a04135f-release.apk`
- 副本：`E:\GQ\One\_forks\OneIms-Lite-HSSkyBoy-v13.6.1-RC2.r1.a04135f-release.apk`

## 验证

- `:manager:assembleRelease`：**PASS**（JDK 21 + SDK 36 + NDK 27 + CMake 3.22.1，`BUILD SUCCESSFUL in 2m 25s`）
- 开机自启实机：NOT RUN（需冷重启 + 已配对 Wi‑Fi + 无线调试/权限就绪）
- **未改** `thedjchi-Shizuku` 自启补丁代码
