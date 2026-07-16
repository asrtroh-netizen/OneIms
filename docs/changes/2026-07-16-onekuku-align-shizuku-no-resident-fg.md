# 2026-07-16 · OneKuku 对齐 Shizuku：废除 App 前台常驻

## 动机

Shizuku 模型（Rikka）：干活的是 adb 拉起的 server，保活 App / 前台通知没有意义。  
OneKuku 的 `OneKukuResidentService` 每 20s FG 轮询是发热反模式。

## 改动

1. `settleOneKukuChannelAfterReady`：始终 `stop` Resident；不再 `start`
2. `OneKukuResidentService.start`：空操作并顺带 stop（防旧入口）
3. 配对成功收尾同步上述策略
4. 文案改为「特权在 onebridge_server」

## 保留

- `onebridge_server` 默认不 pkill（划掉 App 仍可秒醒）
- 休眠状态机 + tcpip 5555（出门保活）
- 开机短生命周期 `OneKukuBootRestoreService`（对齐「打开已就绪」，非永久 FG）

## 验证

- `:app:compileOnekukuDebugKotlin`
- 真机：无「常驻中」通知；杀进程重开应仍秒级 binder 唤醒 — NOT RUN（禁打包）
