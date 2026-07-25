# 2026-07-25 · Caller Directory 对齐 Telo（无悬浮）

## 范围

用户：只要对齐 Telo；不要看来电悬浮。

## 对齐点（公开契约，干净室）

对照 `Pixel-Tailor-CN/PixelTelo` · `TeloDirectoryProvider`：

- `directories` 返回 PACKAGE_NAME / DIRECTORY_AUTHORITY / EXPORT_SUPPORT_ANY_ACCOUNT
- `phone_lookup/*` + `data/phones/filter/*`
- `contacts/lookup/*`（LOOKUP_KEY 回查，`onecaller:` 前缀）
- Cursor 填满 Dialer 常用列：LOOKUP_KEY、MIMETYPE、DISPLAY_NAME、LABEL、NUMBER…

## 差异（有意保留）

- Telo HEAD：Directory **仅骚扰命中**才返回行  
- OneCaller：规则标签 / 拦截 / 本地 geo 均可返回（仍只走 Directory，无悬浮）

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
