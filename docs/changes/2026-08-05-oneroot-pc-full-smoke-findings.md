# OneRoot PC 完全体试跑发现

日期：2026-08-05  
对象：`OneRoot/` Hub（`python oneso.py hub` / `一键启动.cmd`）  
设备：`comet` / `CP2A.260705.006` / `47111FDKD0009J`

## 试跑结果摘要

| 项 | 结果 |
|---|---|
| Hub 启动 | OK · `http://127.0.0.1:<dynamic>/index.html` · version `2026.08.05.1` |
| `/api/ping` | OK |
| `/api/status` | adb OK · so 匹配 `preload-comet-CP2A.260705.006.so` · Shizuku OK |
| dry-run `/api/temp-root` | OK（code=0，未执行） |
| 实跑一键 Root | **FAIL** · 2 轮后仍无 uid=0 · job code=1 |
| 安全清理 | **blocked**（无可用 uid=0） |

## 发现问题

### P1 · 孤儿 `su` 进程（daemon 死、文件已无）

- `ps`：`root 18719 … S su`，另有 zombie 子进程；shell `kill -9` → Operation not permitted
- `/data/local/tmp/su`、sock **均不存在**
- Hub 文案：`临时 Root · 僵尸su(daemon死)`；清理只能 `blocked`
- 影响：后续一键 Root / 清理可能被污染；需成功覆盖 Root 或重启清进程

### P1 · 一键临时 Root 本轮未拿到 uid=0

- so 本地命中 OneSo-assets，push/LD_PRELOAD 有跑
- 第 2 轮 preload 启动仍为 `uid=2000`；`verify su: not uid=0`
- LD_PRELOAD 出现 `rc=124`（超时）痕迹
- 证据：`release/_tmp/hub_job_final.json`、设备 `/data/local/tmp/exploit.log`

### P3 · `favicon.ico` 404

Hub HTTP 日志可见；不影响功能。

### P3 · `OneRoot/README.md` 仍以 `# OneSo` 开头

产品窗已是 OneRoot，文档标题未跟齐。

## 非问题 / 正常

- so 匹配链路对本机 `comet@0705` 工作正常
- dry-run 计划输出完整
- 安全清理在无 root 时拒绝强拆，符合「su-keep / blocked」语义

## 建议下一刀

1. 重启手机清孤儿 `su`，再跑一键 Root 对照是否仍 FAIL  
2. 若仍 FAIL：对照 `exploit.log` 看是否 so/偏移/超时需调  
3. 可选：补 favicon；README 标题改 OneRoot
