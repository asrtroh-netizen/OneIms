# 2026-07-25 · Meter/Caller 抄齐第一刀

## Meter（对标 Pixel Meter）

- 通知显示模式：上下 / 合计 / 仅下 / 仅上 + 自定义前缀
- 可拖动悬浮窗（`SYSTEM_ALERT_WINDOW`）
- QS Tile：`One 网速`、`One 悬浮网速`

## Caller（对标 Pixel Telo）

- Room 大库 `onecaller.db`（DataStore 规则自动迁移）
- 标签匹配 `CallMatchMode.TAG`
- 手动查号 UI

验证：`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug` → SUCCESS
