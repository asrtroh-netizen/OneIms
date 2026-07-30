# 2026-07-29 · OneKuku 六位码通知 FGS 稳态（对齐 V15）

## 结论

先前「V15 binder 重试」**不覆盖**通知栏不稳。V15 稳态靠 `AdbPairingService` 前台服务；OneKuku 原先只 `notify` ongoing，一加/小米等易清。

## 改动

- 新增 `OneKukuPairingHostService`（specialUse FGS）
- `showWaiting` → `startForegroundService` 扛通知
- 新通知渠道 `onekuku_pairing_fgs`（HIGH、无声、无角标）
- 成功/取消时停 FGS

## 产品策略

软件侧不硬拦功能（Compat 降级、IMS 26/27 软失败已落地）；系统上岛随缘。

## 验证

- `compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin`
- 一加真机六位码：NOT RUN
