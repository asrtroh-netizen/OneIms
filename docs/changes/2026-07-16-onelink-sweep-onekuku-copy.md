# OneLink：扫漏网「OneKuku」用户文案

日期：2026-07-16

## 方法

只扫描 **string 值**含 `OneKuku` 的条目（不是 key 名 `onekuku_*`），与 `onelink/.../channel_branding.xml` 做差集后补齐。

## 本轮

1. zh/en branding 补齐约 20+ 漏网 key（激活中、配对通知名、core 下载、termux_hint、reapply_explanation 等）
2. `sanitizeUserText`：OneLink 线把历史 `OneKuku` 字样收成 `OneLink`，并保留 `Shizuku`
3. `SystemApiBroker`：OneLink 错误信息改为 OneLink/Shizuku 表述

## 验证

- 差集：ZH/EN **0 条** string 值仍含 OneKuku 且未 overlay
- `compileOnelinkDebugKotlin` / `compileOnekukuDebugKotlin` PASS

## 刻意不做

- 日志 TAG / 证书 CN / 类名中的 OneKuku（用户不可见）
