# 2026-07-25 · 恢复 Meter 悬浮窗 + 分应用流量

## 背景

减法后用户纠偏：悬浮窗、分应用流量要保留。首页标题沿用 **「网速监测」**。

## 落地

- 从 `8837779^` 恢复 `meter/`、`MeterScreen` 与单测
- Manifest 加回测速 FGS / Tile / 悬浮窗 / Usage Access（不恢复 Caller）
- 首页卡恢复；副标题改为「悬浮窗网速 · 分应用流量 · 可选通知/Tile」

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
