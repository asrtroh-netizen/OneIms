# 2026-07-30 · 产品目标收束：划掉后台要稳

## 用户原话

> 我要的就是稳定，别轻易被划后台干掉就行。

## 现网已具备（默认 ONEBRIDGE，逻辑不变）

| 机制 | 落点 | 作用 |
|---|---|---|
| 划掉不杀通道 | `bridgeBootShellCommand` 默认不 pkill | shell `onebridge_server` 继续活 |
| 防 SIGHUP | setsid / nohup 拉起 | adb shell 流关不死 server |
| PID 重投 binder | `ClientBinderSender` `startedPids` + `onProcessDied` | 划掉再开新 PID 必投递 |
| 前台 0/5/15 复连 | `schedulePrivilegeReconnectShots` | 假未激活窗口对齐 V15 |
| 脏 hint 纠正 | 桥就绪时清 `NEEDS_ACTIVATION` | UI 不卡「未激活」 |

## 本拍工程

- P3a 客户端/模块骨架已铺（为以后内置 MINI）；**默认引擎未切**，不影响现网保活路径
- 清掉误提交的 `care-min/build/`，`.gitignore` 补 `/care-min/build/`

## 真机验收（人工）

1. 激活 READY → `pidof onebridge_server` 有值  
2. 划掉 OneIMS → 同 PID 仍在  
3. 重开 → 数秒内 READY（log：`pid=… starts; send binder` 或 `v15-style reconnect shots`）  
4. 不要求装 `com.onekuku.care`
