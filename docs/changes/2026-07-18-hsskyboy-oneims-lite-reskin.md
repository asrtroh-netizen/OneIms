# 2026-07-18 · HSSkyBoy/Shizuku 底 + 库内（thedjchi V15）皮

工作目录：`E:\GQ\One\_forks\HSSkyBoy-Shizuku`（独立于 OneIMS 主仓；**不改** `thedjchi-Shizuku` 现有自启补丁）

## 决策

| 项 | 选择 | 原因 |
|---|---|---|
| 自启逻辑 | **原样保留 HSSkyBoy** | 现方案（thedjchi 对齐/补丁）仍有问题，停止继续改 |
| UI/品牌 | **库内皮 = thedjchi V15** | Hero + 无线 + 2×2 + 胶囊行；显示名 `Shizuku` |
| 包名 | 上游 `moe.shizuku.privileged.api` | 保持 Shizuku API 契约；与官版 Shizuku 互斥安装 |

## 换皮清单（最新）

- Hero：左侧状态图标 + Active/Inactive + 阶段连线（对齐库内皮 / OneKuku）
- 无线调试卡：按钮顺序 **分步指南 · 配对 · 启动**
- 2×2：应用管理 / 终端 / Root / 电脑 ADB（点击弹窗）
- **启动整卡**：与无线调试同尺寸 chrome，点「设置」弹开机自启/无线自启/看门狗
- **右上角小胶囊**：语言（高亮圆点）+ 主题（OneIMS 胶囊样式）
- 顶栏：去掉 Settings / About；运行中保留「停止」
- 自动化 / 隐身 / 了解更多（对齐库内皮卡片序列）
- 主色 `#0B57D0` / `#A9C7FF`；卡片 20dp
- APK：`Shizuku-v{version}-{variant}.apk` → `E:\GQ\One\_forks\Shizuku.apk`

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

- 构建：`manager/build/outputs/apk/release/Shizuku-v13.6.1-RC2.r4.785160f-release.apk`（约 3.59 MB）
- 交付：`E:\GQ\One\_forks\Shizuku.apk`、`E:\GQ\One\_forks\out\apk\Shizuku.apk`

## 验证

- `:manager:assembleRelease`：**PASS**（本地 Gradle 8.14 + JDK 21，`BUILD SUCCESSFUL in 3m 12s`）
- 开机自启实机：NOT RUN（需冷重启 + 已配对 Wi‑Fi + 无线调试/权限就绪）
- UI 像素级对照截图：NOT RUN（需真机安装对照）
- **未改** `thedjchi-Shizuku` 自启补丁代码
- **未 push** 到 `github.com/HSSkyBoy/Shizuku`（仅本地 commit）
