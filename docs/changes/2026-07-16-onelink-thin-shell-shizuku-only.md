# 2026-07-16 · OneLink 轻壳：重头只认 Shizuku

## 定调

OneLink 不做第二套激活栈；唤醒/保活/耗电由官方 Shizuku 负责。

## 改动

1. `ensureOneKukuReadyForBoot`：`usesShizuku` 时只 wake/授权探测，失败 → NEED_USER，绝不 MiniAdb
2. 首页 `LaunchedEffect` 自动无码直连：仅 `usesEmbeddedBridge`
3. `BootReceiver` Wi‑Fi 续跑：OneLink 跳过（不依赖 ADB STA）

## 验证

- `compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin`
