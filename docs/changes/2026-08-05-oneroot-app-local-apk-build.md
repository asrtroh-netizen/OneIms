# OneRoot App · 本机 assembleDebug 与真机试打

日期：2026-08-05  
范围：`E:/GQ/One/_forks/OneRoot`（手机 App fork）+ 本机 SDK 工具链；**未推 GitHub**。

## 背景

上一拍 OneRoot App 源码/远端已就绪，但本机 APK 标 NOT RUN。本轮按「本机打包、别走 GitHub」在 Windows 工具链上打出 debug APK 并装到真机。

## 本机构建卡点与处置

| 卡点 | 处置 |
|---|---|
| Gradle 拉不到 Google Maven（AGP 9.3.1） | `settings.gradle.kts` 增加阿里云镜像；可选 `.m2-local` 种子仓 |
| 缺 Build-Tools 36.0.0（AGP 9 最低要求） | PowerShell 从 `dl.google.com` 下载 `build-tools_r36_windows.zip` 装入 SDK |
| NDK 偏好 28.2，本机只有 27 | `app/build.gradle.kts` 钉 `ndkVersion = "27.0.12077973"` |
| Lifecycle 2.11 要求 compileSdk 37；`sdkmanager`/JVM 拉 platform 失败 | 构建期 AGP 最终装上 `platforms;android-37.0`；`compileSdk = 37` |
| PATH 默认 Java 8 | 构建时 `JAVA_HOME` 指向 JDK 17（daemon 使用 Gradle 自带 JDK 21） |

## 验证证据

```text
.\gradlew.bat assembleDebug --no-daemon
→ BUILD SUCCESSFUL

APK: app/build/outputs/apk/debug/app-debug.apk
Size: 71477256
SHA256: 92D9287182A5B2EE6FADB3AD05952C9ADCD4693AAA46076A9BC07AC6AFDE16E0

adb devices → 47111FDKD0009J device (comet / Pixel 9 Pro Fold / CP2A.260705.006)
adb uninstall com.oneroot.app → Success（清旧签名）
adb install <apk> → Success
am start com.oneroot.app/.feature.main.MainActivity → 已拉起
截图：主界面 OneRoot + Shizuku 授权弹窗
```

## 真机现象（试打后续）

- UI 已显示 OneRoot 品牌与「基于 Root My Pixel 改编 / OneSo 机型库」。
- 系统弹窗请求 Shizuku 授权（需用户点允许）。

## 追加 · 首页对齐 Shizuku + 赞赏（同日 · 1.0.6）

- 弃用深色 teal 主壳，改 OneIMS/Shizuku 风格浅色首页。
- **状态框 = Shizuku 连接**（粉/白两态 hero + 阶段条）；「激活」按钮对接 `Shizuku.requestPermission` / 拉起 Shizuku。
- 赞赏页：右上角入口 + `assets/sponsor_wechat.jpg`；fork commit `0413335`（未 push）。
- 机型匹配降为次级「机型匹配」卡；家族文案保留。

## 追加 ·「暂无匹配 so」根因（同日 · 1.0.4）

| 项 | 证据 |
|---|---|
| 根因 | `assets/profiles.json` 带 UTF-8 BOM（`EF BB BF`），`kotlinx.serialization` 解析失败 |
| 诊断 | `/data/user/0/com.oneroot.app/files/match-diag.txt`：`Unexpected JSON token... had '﻿'` |
| 修复 | 去掉 BOM + `PayloadLocalDataSource` 读取时 `removePrefix("\uFEFF")` |
| 复验 | `OneRootMatch: ... profiles=40 hits=1`；`Matched profile: comet-CP2A.260705.006` |
| 版本 | `versionName=1.0.4` / `versionCode=5`；fork 本地提交 `e907929`（**未 push**） |
| APK | 已 `adb install`；副本 `/sdcard/Download/OneRoot-1.0.4-debug.apk` |

抽屉侧：PM 已注册 `MAIN/LAUNCHER`；安装后可能被 `PackageUpdateActivity` 盖住。图标已换实心 PNG + splash 主题。若抽屉仍搜不到，可搜「OneRoot」或清 Pixel 启动器缓存。

## 非目标

- 未 `git push` / 未改 GitHub Release。
- 未完成完整 Root 流程冒烟（匹配已通，仍依赖 Shizuku 授权后点安装）。

## 追加 · 正式重装 Platform 37（同日）

用户要求「重新下载下 37」后：

1. 删除伪目录 `platforms/android-37`（原为 android-36 拷贝，`Pkg.Desc=Platform 16`）。
2. PowerShell 重新拉取官方 `platform-37.0_r02.zip`，SHA1=`ed8ebf7f8822a4de5686d427f237d2fa30ff7410`（67,281,901 bytes）。
3. 解压安装到 `platforms/android-37.0`（`Pkg.Desc=Android SDK Platform 17`，`ApiLevel=37.0`，`Pkg.Revision=2`）。
4. 建立 junction：`platforms/android-37` → `platforms/android-37.0`，满足 `compileSdk=37` 查找路径。

## 追加 · OneRoot 1.1.7（同日 · 云端 so 优先）

范围：`E:/GQ/One/_forks/OneRoot`（包名 `com.oneroot.app`）。

| 项 | 内容 |
|---|---|
| 版本 | `versionName=1.1.7` / `versionCode=18` |
| 逻辑 | `PayloadRepositoryImpl.extractPayloads`：**OneSo-assets 云端优先**，失败再试 APK assets（当前 assets 无内置 `.so`） |
| 构建 | `JAVA_HOME=JDK17` + `.\gradlew.bat assembleDebug --no-daemon` → **BUILD SUCCESSFUL**（约 1m15s） |
| 制品 | `E:\GQ\One\_forks\OneRoot\app\build\outputs\apk\debug\app-debug.apk` |
| 副本 | `E:\GQ\One\OneIMS\release\OneRoot-1.1.7-debug.apk`（71,648,874 bytes） |
| SHA256 | `B4C5BDAC4E46EC2E10D9B293DC2271FBC0B79931553DC02AA58D102791744F5A` |
| aapt | `package: name='com.oneroot.app' versionCode='18' versionName='1.1.7'` |
| fork 提交 | 本地 commit（未强制 push） |
| 未做 | 真机 `adb install` / 一键 Root 冒烟（NOT RUN，待授权） |

说明：本拍未重打 Lite/UI 公发包；OneIMS App 侧云端-only so 见 `2026-08-05-temproot-cloud-only-so.md`。
