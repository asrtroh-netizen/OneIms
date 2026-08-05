# 一键临时 Root：自动从 OneSo-assets 取 so

## 行为

- 用户点击一键临时 Root 时，`TempRootSoProvider.ensure`（**远端强制优先**）：
  1. 先刷新并拉取 `https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/catalog.json` + 对应 `so/.../preload-*.so`，校验 `SHA256SUMS`（若可得）
  2. 网络失败时退回 `filesDir/temproot-cache/`
  3. 再退回 APK `assets/temproot/` 内匹配 `DEVICE`+`Build.ID` 的 so（离线兜底）
  4. 再走原有 Download → `/data/local/tmp` → `LD_PRELOAD`

## 成功后配置收尾

- `TempRootPostSuccessActions`：参考最小 Carrier XML（受「持久化改运营商」开关约束）+ `ReapplyManager` 重放用户已存核心/高级选项（触发源 `TEMP_ROOT`）

## 安全

- 仅允许 `https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/` 下的 `.so`
- 拒绝 cleartext、异主机、路径 `..`

## 分支 / 主线

曾在 `feat/temproot-remote-so`；远端优先与 3.2.0 已落 `main`。
