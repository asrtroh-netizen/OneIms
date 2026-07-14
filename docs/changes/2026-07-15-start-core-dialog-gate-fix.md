# 2026-07-15 · 启动核心不弹窗：拆分入口 + 未装核心对话框

## 现象
装了 2.0.15 后点「启动核心」仍不弹六位码。

## 根因
1. 「启动核心」与总控卡共用 `onActivateOneKuku`：`OneKukuManager.isRunning()` 为真时直接走唤醒/授权，**永不进** `prepareOneKukuCore`
2. 核心未安装时只 `publish` snackbar，没有对话框，体感像没反应

## 修复（2.0.16）
- 新增 `onStartCore` → `startCoreFromPrepCard()`，出门激活卡专用，绕过 isRunning 短路
- 未装核心时弹出「还没装 OneKuku 核心」对话框
- versionCode 25 / 2.0.16
