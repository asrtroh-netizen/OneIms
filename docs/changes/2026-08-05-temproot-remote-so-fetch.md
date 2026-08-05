# 一键临时 Root：自动从 OneSo-assets 取 so

## 行为

- 用户点击一键临时 Root 时，`TempRootSoProvider.ensure`：
  1. 优先使用 APK `assets/temproot/` 内匹配 `DEVICE`+`Build.ID` 的 so
  2. 若无：拉取 `https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/catalog.json`
  3. 按目录下载对应 `so/.../preload-*.so`，校验 `SHA256SUMS`（若可得）
  4. 缓存于 `filesDir/temproot-cache/`，再走原有 Download → `/data/local/tmp` → `LD_PRELOAD`

## 安全

- 仅允许 `https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/` 下的 `.so`
- 拒绝 cleartext、异主机、路径 `..`

## 分支

`feat/temproot-remote-so`（commit `feat(temproot): auto-fetch so from OneSo-assets on one-tap`）
