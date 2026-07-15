# 2.0.34 · 图四弹窗即挂配对通知 / 过程态离红

## 问题

1. 点「立即激活」弹出三步说明后，通知栏填码入口要等 `activateExistingOrNeedPair`（mDNS 最长约 6s + 连接探测）结束才挂出，体感「等好半天」。
2. 弹窗期间卡片仍停在 `INACTIVE`（红）；只有就绪后才变白，过程中一直像报错。

## 方案

- 新增 `onBeginWirelessPairGuide`：图四 `AlertDialog` 一经展示（`LaunchedEffect`）立刻 `WAITING_PAIR` + `OneKukuPairingNotification.showWaiting`。
- `prepareOneKukuCore`：先保证通知/相位已就位，再跑慢路径；`pairingUiPrimed` 避免重复 `openWirelessDebugging` 抢会话。
- 过程态由 `WAITING_PAIR` → 卡片 `ACTIVATING`（`primaryContainer`），仅 `READY` 为白；红保留给未激活/失败。

## 版本

- `versionName` 2.0.34 / `versionCode` 43
