# PC OneRoot · 云端 so 优先 + OneSo-assets 写保护

日期：2026-08-05  
范围：`OneRoot/` Hub、`release/oneroot-public` Lite/UI、发包同步脚本。

## 需求

1. PC 版本也不再内置 / 优先本机 so，**全部从 OneSo-assets 云端拉**。
2. 云端 so **默认拒绝任何自动化改写**；只有你主动授权才允许写。

## 变更

| 路径 | 行为 |
|---|---|
| `OneRoot/oneso.py` `resolve_temp_root_so` | `--so` → **GitHub**（catalog+SHA256SUMS 校验）→ `.cache`；移除本机 OneSo-assets / App assets 回退 |
| `oneso.py` `require_oneso_cloud_write` | `pack-0805` / `complete-assets` / 重写 SUMS 默认 blocked；需 `ONESO_ALLOW_CLOUD_WRITE=1` 或 `--allow-cloud-write` |
| `OneRoot/hub.py` | 状态条文案改为「云端 / 云端缓存」 |
| Lite `一键临时Root.cmd` + `sh/fetch-cloud-so.ps1` | 运行时拉云端 so；忽略包内 `so/` |
| UI `TempRoot-UI.ps1` + 同名 fetch 脚本 | 同上 |
| `rebuild_lite_ui_zips.{py,ps1}` | 默认同步 zip 到 OneSo-assets 被跳过，需同环境变量 |

## 验证

见交付总结命令回显（GitHub 拉取 comet/0705、写保护拒绝、`--allow-cloud-write` 放行路径）。

## 非目标

- 未重打 Lite/UI 公开发布 zip（源已改，发包另议）。
- 未删除历史包内 `so/` 文件（已被运行时忽略）。

## 追加 · 云端 P9/P10 全量哈希核验（同日）

实拉 GitHub catalog 全部 40 条 so，对照云端 `SHA256SUMS` + 本机 `OneSo-assets/SHA256SUMS`：

| 指标 | 结果 |
|---|---|
| total | 40 |
| OK | **40** |
| MISMATCH | 0 |
| DOWNLOAD_FAIL | 0 |
| `64ed9d74` 命中 | **0** |
| comet/0705 | `e74cbc7d…`（成功基线） |

证据：`release/_tmp/cloud_p9p10_hash_audit.json`
