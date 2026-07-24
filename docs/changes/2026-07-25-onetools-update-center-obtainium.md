# 2026-07-25 · OneTools 内置更新中心（Obtainium 官方 APK 路径）

## 决策

用户要求 Obtainium「内置」。因 Obtainium 为 **GPL-3 Flutter 完整应用**，本期采用：

- **内置更新中心**（自研 Kotlin）：GitHub Release 检查 / 下载 / 安装  
- **一键获取官方 Obtainium APK**（独立程序，附源码链接与 NOTICE）  
- **不**合并 Flutter 源码进 OneTools（避免整体被迫 GPL + 引擎膨胀）

若未来要「源码级内嵌」，需单独拍板：OneTools 整体 GPL + Flutter Module。

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug` → SUCCESS
