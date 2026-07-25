# 2026-07-25 · OneTools Debug 包反馈五连修

用户完整反馈（含悬浮网速闪退优先）。

## 变更

| # | 项 | 处理 |
|---|---|---|
| 1 | 标题与状态栏重叠 | `OneToolsPage` / `OneToolsToolHeader` / Dock 内页补 `statusBarsPadding` |
| 2 | 底部 Dock | `OneToolsScaffold` + `ToolsDestination`（首页/来电/网速/电池/更多），对齐 OneIMS 悬浮岛 |
| 3 | 按需录音 | 接通弹出悬浮按钮，点按才录；挂断停；需悬浮窗权限 |
| 4 | 取消拦截 | `OneCallScreeningService` 恒放行；UI 去掉拦截动作与默认拦截角色引导 |
| 5 | 悬浮网速闪退 | Overlay/WM 全部切主线程；去掉双击路径 `runBlocking` |

## 验证

- `:onetools:testDebugUnitTest` PASS
- `:onetools:assembleDebug` PASS
- 真机悬浮网速 / Dock / 归属 / 录音按钮 **待用户复测**
