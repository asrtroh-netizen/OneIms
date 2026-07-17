# 2026-07-17 · Shizuku 分叉三连修（V15.0）

## 归属仓

`E:/GQ/One/_forks/thedjchi-Shizuku`

## 需求

1. 修正「四个变三个」
2. 版本 13.6 → V15.0
3. 去掉 Buy me a coffee

## 方案

| 项 | 根因 | 修复 |
|---|---|---|
| 四变三（快捷区） | 非 Root 时 `tileRoot.isVisible = false`，2×2 只剩 3 格 | 始终显示四格；无 Root 时禁用并提示改用无线/PC ADB |
| 四变三（状态条） | `READY` 只点亮 3/4 阶段点 | `READY`/`SLEEPING` 均点亮 4 |
| 版本 | `build.gradle` 已是 15.0.0；备份文件仍为 13.6.0 | 统一 `baseVersionName = "V15.0"`；APK 名改为 `shizuku-${versionName}-*.apk` 避免 `vV` |
| Donate | About 弹窗 `btnDonate` + BMC 链接 | 移除按钮、字符串、图标、FUNDING 条目 |

## 验证

- 静态检索：`13.6` 仅可能残留于无关 SVG 路径数字（已删 BMC 图标）
- 编译：见本轮 `./gradlew :manager:compileDebugKotlin` 结果
