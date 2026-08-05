# OneRoot Lite/UI 同步 Hub「清理残留」

## 交付

公开包提供与本地 Hub `cleanup_temp_root_residuals` 同语义的清理入口：

| 入口 | Lite | UI |
|---|---|---|
| 双击 | `清理残留.cmd` | `清理残留.cmd` |
| 参数 | `一键临时Root.cmd cleanup` / `cleanup-aggressive` | 同左 |
| 窗体 | N/A | 底部「清理残留」按钮 |
| 脚本 | `sh/cleanup-residuals.sh`（**LF**） | 同左 |

## 行为

1. shell：杀挂起 LD_PRELOAD / 尽力删 `temp_su.sock`（及尝试删 `su`）
2. 若已有 uid=0：`su` 清 sock；`cleanup-aggressive` 再删 `/data/local/tmp/su`
3. 无 uid=0：`CLEANUP PARTIAL (blocked)` + 提示

## 验证

- Lite `cleanup nopause` → `CLEAN_SHELL_OK` + `CLEANUP PARTIAL`（当前设备无 root）
- UI `-Cleanup -AutoClose` → status `CLEANUP_PARTIAL mode=blocked`
- 设备脚本必须 LF；CRLF 会导致 `unexpected 'do'`
