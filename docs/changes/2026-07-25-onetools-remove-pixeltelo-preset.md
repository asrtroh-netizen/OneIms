# 2026-07-25 · 移除 Pixel Telo 更新预设（含 fork）

## 背景

减法后应用内 Telo 页已删；更新中心仍默认挂 `Pixel-Tailor-CN/PixelTelo`（fork）GitHub 源。用户要求去掉。

## 落地

- `TrackedApps.presets` 删除 `gh-pixeltelo`
- `NOTICE` 同步：禁止再挂 Pixel Telo / fork 预设

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
