# 清理残留不可把活 Root 变成僵尸

## 现象

Hub 点「清理残留」后，从可用 `uid=0` 变成：
`su: connect daemon: No such file or directory` + root 属主 `/data/local/tmp/su` 仍在。

## 根因

旧 `cleanup_temp_root_residuals(aggressive=False)` 在有 uid=0 时执行 `CLEAN_VIA_SU`：**只删 sock、默认不删 su 二进制** → 人造僵尸。

## 修复

| 模式 | 行为 |
|---|---|
| 安全（默认） | 只杀挂起 exploit；**有 uid=0 则保留 daemon**（`mode=su-keep`） |
| 强力（aggressive） | 一次拆除 sock + su 二进制（`TEARDOWN_VIA_SU`） |
| 无 root | 尽力 shell 清理；僵尸则 `blocked` |

Hub 清理按钮：先确认安全清理；取消后再确认才强力拆除。  
Lite/UI 同步：`cleanup` = keep；`cleanup-aggressive` = teardown。

## 验证

- 源码标记：`su-keep` / `TEARDOWN_VIA_SU` / KILL_STUCK 不含 rm su — **OK**
- 有 uid=0 时点安全清理仍保持 root — **NOT RUN**（本轮末设备掉线 / exploit 未打上）
