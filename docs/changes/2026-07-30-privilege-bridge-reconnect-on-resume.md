# 2026-07-30 · 退出再开特权桥掉线（小米双版本）

## 现象

小米上 OneKuku / OneLink：退出后再打开，状态框偶发回退为未激活（特权桥掉）。

## 根因

1. `wakeChannelWhenForegrounded` 仅在 `isReady()` 时唤醒，桥已断则什么都不做。
2. OneLink 冷启 `LaunchedEffect` 直接 return，不等 Shizuku binder 晚到。
3. `binderDeadListener` 无条件把 running/granted 打成 false，Shizuku 授权粘滞时也会假掉。

## 修复

- 前台：先 resync；未就绪则 OneLink 轮询 binder / OneKuku 走 `prepareOneKukuCore`。
- 冷启：OneLink 同样短轮询对齐状态。
- binder dead：只按桥真值刷新，禁止硬清 granted。
- ON_RESUME：额外 sync 一次。

## 验证

- 编译双 flavor
- adb：force-stop → start → 查 running/granted 与 session 日志
