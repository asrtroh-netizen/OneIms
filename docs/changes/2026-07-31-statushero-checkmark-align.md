# StatusHero：✓ 与大字标题对齐并收高度（3.1.0 覆盖）

## 动机

去掉 Lite 眉题小字后，状态卡仍可能因「空眉题行 + 设备详情」把 ✓ 顶对齐到标题上方；需要与大字通道名垂直对齐，并收紧上下留白。

## 改动

- `StatusHero`：✓ 与标题/Active 同行 `CenterVertically`
- 无眉题时不再留空行；设备详情挪到标题行右侧
- 有眉题（OneKuku）时眉题单独一行，✓ 仍贴大字
- 收紧 `cardPad` / `blockGap` / 图标尺寸

## 发布

- 同号覆盖 GitHub Release `v3.1.0` 双 APK
- README What’s New · 3.1.0 增补对齐说明；README-only 推 `origin/main`
