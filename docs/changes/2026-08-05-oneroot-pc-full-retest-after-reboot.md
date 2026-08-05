# OneRoot PC 完全体 · 重启后复测

日期：2026-08-05  
对象：`OneRoot/` Hub（已运行 `oneso.py hub` · `http://127.0.0.1:51628`）  
设备：`comet` / `CP2A.260705.006` / `47111FDKD0009J`  
背景：上一轮发现孤儿僵尸 `su`；用户重启手机后要求再测 PC 完全版。

## 复测结果摘要

| 项 | 结果 |
|---|---|
| 设备 adb | OK · `comet` · `CP2A.260705.006` |
| 孤儿 `su` | **已清除**（重启生效；`ps` 无 su；`/data/local/tmp/su` 不存在） |
| Hub `/api/ping` | OK · version `2026.08.05.1` |
| `/api/status` | adb OK · so 匹配 `preload-comet-CP2A.260705.006.so` · Shizuku OK · **临时 Root 未检测到**（文案已非「僵尸su」） |
| 实跑 `POST /api/temp-root` `run=true` | **FAIL** · job `code=1` · stage=`失败：2 轮后仍无 uid=0` |
| 结束后 root | `root_ok=false` · SELinux=`Enforcing` · 仍无 `/data/local/tmp/su` |

## 与重启前对照

| 维度 | 重启前 | 重启后复测 |
|---|---|---|
| 僵尸 su | 有（daemon 死、shell 杀不掉） | **无** |
| status 文案 | `临时 Root · 僵尸su(daemon死)` | `临时 Root · 未检测到` |
| 一键 Root | FAIL · 2 轮无 uid=0 · `ld_preload rc=124` | **同形 FAIL** · `LD_PRELOAD#2 TIMEOUT after 95s` · `rc=124` |
| SELinux（preload 上下文） | 上一轮日志见 `enforce=0` / Permissive | 本轮 `enforce=1` / **Enforcing** |
| pipe stage | `install_ok=0` | `install_ok=0`（同） |

## 结论

1. **重启成功解决了孤儿 su 污染**，不再是清理 blocked / 僵尸文案。  
2. **一键临时 Root 仍失败**，说明主因不是僵尸 su，而在 **so / LD_PRELOAD / mode0·configfs 路径本身**（与既有 `oneroot-fail-kernel-panic-mode0` 采证同族：`slide-kaslr-ok` → `mode=0` → configfs 环 → 无 uid=0）。  
3. 本轮未见整机 panic，但两轮均卡在 ~95s 超时，最终 `verify su: not uid=0`。

## 证据指针

- Job 摘要：`release/_tmp/hub_job_retest.json`
- 设备 log 拉取：`release/_tmp/exploit_retest.log`（注意设备侧 mtime 可能偏旧，以 Hub job log 为准）
- Hub session：`OneRoot/logs/session-20260805-221327`

## 建议下一刀

1. 停连续硬点一键 Root（抬高 panic 风险）。  
2. 聚焦 `preload-comet-CP2A.260705.006.so` 的 mode0/configfs / `install_ok=0`。  
3. 可选对照：若能在可控环境临时 Permissive 再跑一轮，区分 SELinux 是否为硬门槛（需明确授权，默认不做）。
