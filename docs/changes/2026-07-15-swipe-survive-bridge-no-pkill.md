# 划掉 App 后通道保活（对齐 Shizuku）· 2.1.8

## 现象

划掉 / 强停 App 后 `onebridge_server` 仍在，但重开会 `pkill` 再建新进程，UI 再闪「激活中」。

## 根因

1. `bridgeBootShellCommand` 默认一律 `pkill` 再拉起。
2. `BridgeService` 只投递一次 binder；App 进程死后内存 holder 清空，旧 server 不会再投。

## 修复

1. 默认启动：`pidof onebridge_server` 已在则只 echo OK，不杀；`forceRestart=true`（设置「重新激活」）才 pkill。
2. `BridgeService` 经 Uid/Process Observer 在客户端起来时重投 binder（**已不再做 3s 死循环**；2026-07-30 起 ProcessObserver 按 **PID** 去重，见 `2026-07-30-onebridge-pid-binder-resend.md`）。
3. `prepareOneKukuCore`：静默 wake 失败后先等最多 9s binder，再才进 CONNECTING / ADB。

## 验证建议

1. 激活就绪后记 `pidof onebridge_server`。
2. 划掉 App → 同 PID 仍在。
3. 重开 → 同 PID，UI 很快「已就绪 · 常驻」，不因 pkill 换 PID。
