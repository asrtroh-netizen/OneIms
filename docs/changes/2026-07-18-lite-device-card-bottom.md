# OneLite 设备卡回底部 + 设备详情跟主题（2026-07-18）

## 改动

- **OneLink / Lite 首页**：恢复底部 `DeviceDetailsCard`（列表末项），不再只靠状态卡胶囊弹窗。
- **设备详情（两版共用）**：正文色改为 `onSurface`，卡片底改为 `surfaceContainerLow`，修复深色主题下硬编码深字几乎看不见的问题。

## 验证

- `:app:packageNamedOnelinkDebugApk` / `packageNamedOnekukuDebugApk` + `adb install -r` → Success
