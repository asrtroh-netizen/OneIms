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
- 在未授权前界面提示「Shizuku 未连接」与「当前设备/版本暂无匹配 so」——`profiles.json` 已含 `comet-CP2A.260705.006`，需授权后复验匹配链路。

## 非目标

- 未 `git push` / 未改 GitHub Release。
- 未完成完整 Root 流程冒烟（依赖 Shizuku 授权与 so 匹配复验）。

## 追加 · 正式重装 Platform 37（同日）

用户要求「重新下载下 37」后：

1. 删除伪目录 `platforms/android-37`（原为 android-36 拷贝，`Pkg.Desc=Platform 16`）。
2. PowerShell 重新拉取官方 `platform-37.0_r02.zip`，SHA1=`ed8ebf7f8822a4de5686d427f237d2fa30ff7410`（67,281,901 bytes）。
3. 解压安装到 `platforms/android-37.0`（`Pkg.Desc=Android SDK Platform 17`，`ApiLevel=37.0`，`Pkg.Revision=2`）。
4. 建立 junction：`platforms/android-37` → `platforms/android-37.0`，满足 `compileSdk=37` 查找路径。
