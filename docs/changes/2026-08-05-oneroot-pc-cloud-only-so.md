# 2026-08-05 · OneRoot PC / Lite / UI so 仅云端拉取

## 决策

- **PC Hub（`OneRoot/oneso.py`）**：运行时 so = GitHub OneSo-assets → `.cache`（须过 `SHA256SUMS`）；**不再**读本机 OneSo-assets clone / App assets / 发包内置 so
- **Lite / UI 便携包**：删除包内全部 `preload-*.so`；`fetch-cloud-so.ps1` 拉云端并强制哈希一致
- **云端写保护**：`complete-assets` / `pack-0805` / catalog·SUMS 重写默认拒绝；仅你主动设 `ONESO_ALLOW_CLOUD_WRITE=1` 或 `--allow-cloud-write` 才放行
- **1.1.7 APK**（既有）：`com.oneroot.app` / `versionName=1.1.7` / 无内置 preload so

## 验收（本轮）

| 项 | 结果 |
|---|---|
| APK | `release/OneRoot-1.1.7-debug.apk` · aapt `1.1.7` / vc `18` · preload so=0 |
| Hub fetch | `comet/CP2A.260705.006` → sha `e74cbc7d…` · classify=`cache`（来自 GitHub） |
| 写保护 | `python oneso.py complete-assets` → blocked / exit≠0 |
| Lite/UI zip | so_count=0 · 含 `fetch-cloud-so.ps1` · 未同步进 OneSo-assets（无写授权） |

## 产物

| 路径 | 说明 |
|---|---|
| `OneRoot/oneso.py` | 云端优先 + SUMS 强制 + 写保护 |
| `release/oneroot-public/oneroot/{Lite,UI}/` | 无 so/；说明已更新 |
| `release/OneRoot-Lite.zip` / `OneRoot-UI.zip` | 重建（~3MB 级，无 so） |
| `release/OneRoot-1.1.7-debug.apk` | 既有 1.1.7 |

## 真机

- Hub / Lite 一键 Root 实跑：**NOT RUN**（本拍无授权设备时 `adb device=?`）
