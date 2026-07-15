# 打开首页卡顿：去掉双跑激活 + 无线调试按需短等

## 现象

已配对设备打开 App 时，通道卡片长时间停在「连接中/激活中」，体感「打开卡老半天」。

## 根因

1. 进首页同时 `enqueue` 开机编排 + `prepareOneKukuCore`，两边抢同一 `activateMutex`，耗时叠加。
2. `tryEnableAdbWifi` 成功后无论本来是否已开，都硬等 3s。

## 修复

1. 已配对前台打开：只走 `prepareOneKukuCore`，不再踢开机编排（冷开仍靠 BootReceiver）。
2. `ensureAdbWifiEnabled` 区分 ALREADY_ON / ENABLED_NOW；仅刚打开时短等 1.2s。
3. `prepareOneKukuCore` 先 `wake()` 快路径，桥已在则跳过 mDNS。

## 验证

- 杀进程重开：首屏应立刻可交互；若通道本就在，应秒级变已激活。
- 无线调试本已开：日志应见 `already on, skip wait` / 无 3s 硬等。
