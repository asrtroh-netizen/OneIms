# 2026-08-05 · P9/P10 so 按验证清单对齐

## 背景

comet@0705 热补丁 so `64ed9d74…` 导致 PC 一键 Root FAIL；换回清单原版 `e74cbc7d…` 后成功。  
按同一标准，将 **P9/P10 全系列** 对齐到 `OneSo-assets/SHA256SUMS` 验证集，并同步到 App assets 与发包目录。

**不做**：把 comet 二进制盲拷到其它机型（P9/P10 二进制不可交叉）。

## 结果

| 项 | 结果 |
|---|---|
| OneSo-assets vs SHA256SUMS | **40/40 OK**（无 MISMATCH；无残留 `64ed9d74`） |
| App `temproot/` | 全量 40 个 so 哈希对齐；`catalog.json` 升为含 P9/P10 全构建条目（本地文件名） |
| Lite/UI / zipstage / oneroot-public so | 各目录 40/40 对齐 + catalog 同步 |
| Hub `.cache/so` | 已同步 |

## 证据

- `release/_tmp/p9p10_so_align_report.json`
- `release/_tmp/p9p10_so_sync_report.json`

## 验证

- App assets：40 文件 sha == SUMS
- OneRoot-Lite so：40 文件 sha == SUMS
- 真机全机型一键 Root：**NOT RUN**（仅 comet@0705 本夜已成功）
