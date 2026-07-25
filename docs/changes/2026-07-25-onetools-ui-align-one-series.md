# 2026-07-25 · OneTools UI 对齐 One 系列

## 目标

用户：「UI全部对齐One系列」→ OneTools 与 OneIMS 共用同一套页面骨架 / token / 主按钮语言。

## 落地

| 项 | 说明 |
|---|---|
| Theme | `OneToolsTokens` 补齐 pressed/divider；默认 `dynamicColor=true` |
| 组件 | `OneToolsPage` / `OneToolsToolPage` / `OneToolsToolHeader` / `OneToolsInfoCard` / `OneToolsPrimaryButton` |
| 首页 | `OneToolsPage` + 分区标题 + 20dp 卡片 |
| 子页 | 去掉「← 文案」返回，统一箭头顶栏（电池 / 更新 / 录音 / Hub / Meter） |
| Hero 按钮 | `PrimaryPillButton` 委托 `OneToolsPrimaryButton`（白底黑字胶囊） |

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
