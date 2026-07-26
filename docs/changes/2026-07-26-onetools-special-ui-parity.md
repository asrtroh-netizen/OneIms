# 变更说明 · OneTools 特色功能 UI 完整复刻 OneIMS

## 背景

R1 已把信号格 / 5G 显示 / 控制中心切卡迁入 OneTools，但 UI 被简化成「标题 + 单行单选 + 按钮」，与 OneIMS 独家页差异明显。

## 本轮

以 `be86475^` 的 `ExperimentalScreen` 三项区块为真源，在 OneTools 内完整复刻：

- 选卡胶囊（`OneToolsSelectedSimPill`）
- 带副标题的 `SettingsChoiceRow` / `SettingsSwitchRow` / `SettingsActionRow` + 分组分隔
- 5G 自定义模式：4 个速率阈值字段 + 系统图标配置串（阈值持久化对齐 OneIMS）
- 切卡：刷新 SIM、`切到卡N` 按钮、确认对话框
- 文案对齐 OneIMS（品牌处改为 OneTools）
- 版本 → `0.3.7` / `19`

## 验证

```text
.\gradlew :onetools:compileDebugKotlin
powershell -File onetools/scripts/build-local-apk.ps1
```

真机对照截图：**NOT RUN**（需安装 `onetools/dist/OneTools-v0.3.7-latest-debug.apk`）。
