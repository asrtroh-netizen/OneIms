# 2026-07-30 · 复连少碰 ADB（消「USB 调试」弹窗）

## 用户现象

划掉/复连时反复弹出「USB 调试已连接/断开」；用户认为只该用无线调试。

## 说明

1. **系统文案**：无线调试走的仍是 `adbd`，通知常写成「USB 调试」，**不等于数据线**。
2. **触发抖动**：前台复连多次 `libadb shell:` / `tcpip:` → adbd 会话连断 → 弹窗刷屏。
3. **PC 侧**：本机用 USB `adb install` 测机也会真实弹 USB 调试（与 App 内无线路径无关）。

## 修复

`schedulePrivilegeReconnectShots`：前 **15s 只 wake + 等 binder 重投**，不调用 `prepareOneKukuCore`；超时才 **一次** ADB 回落。

## 验证

- 编译装包；划掉再开 log 应见 `wait-binder-only`，不应连续 `EmbeddedAdb shell out=`
- 弹窗应明显减少（系统仍可能在首次 ADB 回落时弹一次）
