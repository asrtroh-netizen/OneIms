# 2026-07-15 · 填码后卡住：pair 超时 + 激活串行化（2.0.31）

## 根因
1. manager.pair() 无超时，异常时通知栏「配对中」会长时间不返回。
2. 确认激活时同时 openWireless + ctivate，易与通知栏填码并发抢同一 AdbConnectionManager。

## 修复
- pair 独立线程 + 12s 超时
- activate Mutex 串行
- post-pair mDNS 缩到 3s
- 确认弹窗只走 onActivateOneKuku（内部已开无线调试）

## 版本
- 2.0.31 / versionCode 40
