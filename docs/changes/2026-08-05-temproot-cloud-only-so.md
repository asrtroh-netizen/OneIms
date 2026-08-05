# 2026-08-05 · App 临时 Root so 改为仅云端拉取

## 决策

- **不发包**、**不内置** P9/P10 `preload-*.so` 进 APK
- **全部从云端** `OneSo-assets`（GitHub raw）拉取；失败仅用本机 `temproot-cache`

## 改动

- 删除 `app/src/main/assets/temproot/*.so`（含此前对齐同步的全家桶）
- `catalog.json` → v3：仅保留 `so/...` 远端路径提示，无本地 blob
- `TempRootSoProvider.ensure`：去掉 APK assets 回退

## 验证

- assets 目录仅剩 `catalog.json`
- 代码路径：remote → cache → null
- 真机一键 Root（需联网）**NOT RUN**（本拍未装包）
