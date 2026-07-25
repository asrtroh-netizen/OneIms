# 2026-07-25 · 恢复 Caller（归属+拦截）并收敛 Meter 定位

## 背景

减法后用户纠偏：

- **Caller**：要「来电归属 + 骚扰拦截」（干净室自研，不合并 Pixel Telo 源码）
- **Meter**：只要类 Pixel Meter（通知/悬浮）+ 分应用流量；不做 GlassWire/OEM/套餐杂项

## 变更

- 恢复 `caller/`、`CallerScreen`、Room/KSP、CallScreening Manifest 与相关单测
- 首页恢复「来电归属与拦截」入口；去掉应用内 Telo 按钮
- Meter 页已去掉 OEM dock UI；文案改为「类 Pixel Meter · 悬浮/通知 · 分应用流量」
- 架构蓝图 In/Out 同步（F7/F8；Telo 对照仍 Out）

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
→ BUILD SUCCESSFUL
```

## 非目标（本轮不做）

- 重新创建 PixelTelo fork / 应用内 Telo 对照
- Meter 套餐限额、GlassWire 防火墙、OEM SystemUI 硬贴
- `git push`（本地提交即可）
