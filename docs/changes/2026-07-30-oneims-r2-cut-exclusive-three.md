# 2026-07-30 · OneIMS R2：砍掉三项独家运行时路径

## 三项（定义不变）

1. 信号格显示样式（含功能页残留的信号强度阈值写入）
2. 5G 显示增强
3. 控制中心快捷切卡

> OneTools「特色功能」保留上述能力；本轮只收敛 **OneIMS**。

## 本轮做了什么

- **功能页**：移除信号强度调整开关；一键应用核心不再写 `SystemDisplayOverrideManager`
- **开机重放**（`ReapplyManager`）：不再重放 5G 显示 / 信号阈值 / 信号格样式
- **一键恢复**（`OneKukuRestoreManager` / 快照工厂）：不再恢复 `signal` / `five_g_display`
- **展示**：状态摘要与网络类型标签不再挂 5G 显示增强文案
- **切卡**：删除 `DataSimSwitchTileService` / `DataSimSwitchManager` / `QuickSettingsTileHelper` 与对应单测
- **应急回滚**：`SafetyGuard.restoreDefaults` 仍清理旧显示 ownership / 本地 prefs（避免旧用户被静默重放）

## 明确未做

- **未编译**（用户要求「先不编译」）→ 编译门禁 **NOT RUN**
- 未删除 `SystemDisplayOverrideManager` / `SignalBarSystemStyleManager` / `FiveGSignalReader` 等底层文件（仅断产品入口；可后续再做死代码清扫）
- 未改 OneTools
- 未升 versionCode

## 人工验证清单（编译后）

1. 功能页无「信号强度调整」；一键应用只动 IMS + 5G NR
2. 独家页仍只有身份 / APN / 专家等保留项
3. 控制中心无 OneIMS 切卡磁贴
4. 开机后不再写回旧信号格 / 5G 显示偏好
5. OneTools 特色功能三项仍可用
