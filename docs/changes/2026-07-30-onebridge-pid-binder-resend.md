# 2026-07-30 · OneBridge 划掉后台 binder 重投（对齐 V15.0）

## 用户事实

**Shizuku V15.0 划掉后台也能活**；OneKuku 更容易假死 → 特权通道 binder 重投模型不一致。

## 根因

V15 `BinderSender.ProcessObserver` 按 **PID** 去重：新进程必 `sendBinder`，`onProcessDied` 清 PID。

OneBridge `ClientBinderSender` 修前把进程回调也塞进 **UID** `startedUids`，且无 `onProcessDied`。划掉后若 OEM 未及时 `onUidGone`，重开被「Uid already starts」挡掉 → server 仍活但不投 binder。

（旧文档曾写「每 3s 重投」，现码已改为 Observer；以本文件为准。）

## 修复

`bridge/.../BridgeService.kt` `ClientBinderSender`：

- ProcessObserver：`startedPids` + `onProcessDied`
- 前台/状态变化：新 PID 才投递（对齐 V15）

配合既有：默认不 pkill `onebridge_server`、前台 0/5/15s 复连、Provider 侧 living binder 去重。

## 验证

- `./gradlew :bridge:compileReleaseKotlin :app:compileOnekukuReleaseKotlin`
- 真机：就绪后 `pidof onebridge_server` → 划掉 App → 同 PID → 重开应秒级就绪（人工；看 log `pid=… starts; send binder`）
