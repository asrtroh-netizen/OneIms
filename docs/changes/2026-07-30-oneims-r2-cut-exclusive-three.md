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

## 明确保留（用户点名「加上」· 已在当前树）

下列 3.0.6/3.0.7 体验补丁均为 `HEAD` 祖先，R2 砍刀未触碰：

| 能力 | 锚点 commit | 当前证据 |
|---|---|---|
| 三态通道卡（未激活/激活中/就绪，无休眠 UI） | `c0e3be4e0` | `OneKukuCardState` + `StatusHero` 三态注释 |
| 矮屏紧凑布局 | `afdbda2cc` | `rememberHomeCompactLayout()`（矮屏/窄屏/大字号） |
| 假就绪 + Apply 硬门禁 | `b1daf94bf` | `MainActivity.ensurePrivilegedAccess` / granted 同步 |
| Hero 标题吃字修复 | `dd2a4ec60` | `StatusHero` 标题独占行，胶囊分行 |

验证：`git merge-base --is-ancestor <commit> HEAD` 上述四条均为 true（2026-07-30）。

## 明确未做

- **未编译**（用户要求「先不编译」）→ 编译门禁 **NOT RUN**
- 未删除 `SystemDisplayOverrideManager` / `SignalBarSystemStyleManager` / `FiveGSignalReader` 等底层文件（仅断产品入口；可后续再做死代码清扫）
- 未改 OneTools
- 未升 versionCode
- 「加上三态/矮屏/假就绪/吃字」**无需再合代码**（已在树内）

## 命名与发包（用户 2026-07-30 拍板 · 已更正）

- **不发包**：不新建 GitHub Release、不上传 APK、不改远程 tag。
- **命名为 `3.0.9` / versionCode `79`**（更正：不是继续叫 3.0.8）。
- 公开下载区仍可指向已发版的 3.0.8；本地产物文件名随 gradle 变为 `…-3.0.9.apk`。
- 证据：`app/build.gradle.kts` → `oneImsVersionName = "3.0.9"`，`versionCode = 79`。

## 人工验证清单（编译后）

1. 功能页无「信号强度调整」；一键应用只动 IMS + 5G NR
2. 独家页仍只有身份 / APN / 专家等保留项
3. 控制中心无 OneIMS 切卡磁贴
4. 开机后不再写回旧信号格 / 5G 显示偏好
5. OneTools 特色功能三项仍可用
