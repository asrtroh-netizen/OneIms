# 2026-07-30 · OneKuku 学前 V15.1.0：前台 0/5/15s 特权复连

## 背景

lite（外置 Shizuku V15.1.0）正常，OneKuku 更容易「假死 / 假未激活」→ 差分在 **OneBridge 特权通道**（App 划掉后 binder 必丢），非 IMS 业务。

开机路径此前已对齐 V15（UserPresent / WifiReady / Watchdog）。缺口在 **前台 UI**：`wakeChannelWhenForegrounded` 只单次 `prepare`，`binderDead` 只 resync。

## 改动

`MainActivity.kt`：

- 新增 `schedulePrivilegeReconnectShots`：已配对且非 Shizuku 线时，按 **0 / 5s / 15s** 错峰再拉（末拍 `forceRestart`）；就绪则 `settle` 清脏 hint。
- 前台未就绪 → 走多拍，不再单次 prepare。
- `binderDead` → resync 后追加多拍（与 Watchdog 后台静默重拉互补）。

## 刻意不学

Manager 级 SelfStarter FGS / 写 `adb_wifi_enabled`（属外置 V15.1.0 职责）。

## 验证

- `./gradlew :app:compileOnekukuReleaseKotlin`
- 真机：划掉后台再开 / 人为断 binder → 查 session 日志含 `v15-style reconnect shots`（人工）
