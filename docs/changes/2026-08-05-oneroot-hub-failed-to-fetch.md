# 2026-08-05 · OneRoot Hub「Failed to fetch」他机修复

## 现象

他机（Win10 + Python 3.14）打开 OneRoot UI 后：

- 文案：本地服务未响应
- 检查项：`本地 API TypeError: Failed to fetch`
- 日志：`[boot] FAIL TypeError: Failed to fetch`

## 根因（已复现）

`boot()` 先 `/api/ping`（200）再 `/api/status`。  
`status` → `resolve_temp_root_so` → `oneims_root(cfg)`：当 `config.json` 仍指向作者机 `E:/GQ/One/OneIMS` 且他机无该树时 **`raise SystemExit`**。  
HTTP handler 只 `except Exception`，抓不住 `SystemExit` → 连接被掐断 → Chromium 报 `TypeError: Failed to fetch`（本机复现为 `RemoteDisconnected`）。

无 adb 设备时更容易踩中（缓存 so 快路径不会挡住 `oneims_root`）。

## 修复

| 文件 | 改动 |
|---|---|
| `OneRoot/oneso.py` | `resolve_temp_root_so` 软解析 `oneims_root`，无效则 `None`，禁止 SystemExit |
| `OneRoot/hub.py` | `/api/status` 额外捕获 `SystemExit` → JSON 500 |
| `OneRoot/web/app.js` | 检测 `file://` 并给出可读错误 |
| `OneRoot/OneRoot.ps1` | 启动提示勿关黑窗 |
| `release/oneroot-stage/*` | 同源同步 |

## 验证

```text
坏 oneims_root + 无设备 → 修复前 status RemoteDisconnected
修复后 → /api/status HTTP 200（overall=warn）
```
