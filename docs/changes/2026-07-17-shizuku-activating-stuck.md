# 2026-07-17 · Shizuku 配对后卡在「激活中」

## 现象

配对完成点启动，Hero 长期「激活中」，主按钮灰，应用管理仍「请先启动」。

## 根因

`ShizukuStateMachine.STARTING` 是粘性陷阱态：

- Worker `CancellationException` / 通知取消不 `update()`
- `setDead()` 在 STARTING 时保持原态
- 再点启动被 Toast 挡死；Hero ACTIVATING 按钮禁用

## 修复（`thedjchi-Shizuku`）

- `setDead`: STARTING → STOPPED
- `AdbStartWorker` / `NotifCancelReceiver`: 取消后 `update()`
- `AdbStarter`: 失败路径离开 STARTING
- `ShizukuReceiverStarter`: 已 STARTING 勿再 REPLACE enqueue
- `StartWirelessAdbViewHolder` + Hero：允许清除孤儿 STARTING 并重试

## 验证

- `:manager:compileDebugKotlin` PASS
- 真机：配对→启动应 ≤60s READY，或失败回落可重试（人工）
