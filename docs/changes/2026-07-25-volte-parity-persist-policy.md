# 2026-07-25 · 免 Shizuku 对齐 VoLTE 策略

## 用户定调

> 能不用 Shizuku 的全部像 VoLTE 一样实现；不行的就等连无线。

## 落地

| 类 | 含义 | 本轮动作 |
|---|---|---|
| A | 真持久 / Provisioning，开机免 Shizuku | 沙盒旁路**默认开**；VoNR 开时写 NR availabilities；VoLTE 仍走 Provisioning |
| B | 必须特权通道重放 | OneKuku 开机协调器已等 Wi‑Fi/无线后再写；Lite 仍依赖 Shizuku |

## 代码

- `ConfigStore.isSandboxPersistBypass` 默认 `true`
- 首页文案同步「默认开」
- 既有 BootRestore「等无线」逻辑保持，不改为依赖 Shizuku
