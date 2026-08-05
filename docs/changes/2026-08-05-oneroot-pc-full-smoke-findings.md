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

---

## 复测（手机重启后 · 2026-08-05 夜）

前提：用户已重启手机；复用已运行 Hub `pid=86644` · `http://127.0.0.1:51628` · version `2026.08.05.1`。  
设备仍为 `comet` / `CP2A.260705.006` / `47111FDKD0009J`。

### 复测结果摘要

| 项 | 结果 |
|---|---|
| ADB | OK · device |
| `/api/ping` | OK |
| `/api/status`（跑前） | adb OK · so OK · **临时 Root · 未检测到**（僵尸 su 已消失）· Shizuku OK |
| dry-run `/api/temp-root` | OK · code=0 |
| 实跑一键 Root | **仍 FAIL** · job code=1 · `失败：2 轮后仍无 uid=0` |
| `/data/local/tmp/su` | 仍不存在 |
| 孤儿 `su` 进程 | **已清**（重启有效） |

### 结论对照

1. **上一刀建议 #1 已执行**：重启后孤儿 `su` / 僵尸态消失，status 不再报「僵尸su(daemon死)」。  
2. **根因不在僵尸 su**：清干净后 LD_PRELOAD×2 仍各卡满约 90s → `ld_preload rc=124`，`verify su: not uid=0`。  
3. **exploit 侧信号**（`/data/local/tmp/exploit.log`）：preload 以 `uid=2000` 启动；见 `delta=0000000000000000`；长时间停在 `pipe stage attempt=1/72`，未在超时前完成提权。  
4. Hub / so 匹配 / 计划链路本轮正常，失败点在 **exploit 90s 内未出 uid=0**（偏移/稳定性/超时预算），不是 PC 完全体启动或连机问题。

### 本轮证据

- `release/_tmp/hub_retest_pre.json` · `hub_retest_final.json` · `hub_retest_job.log`
- 设备：`/data/local/tmp/exploit.log`（约 530KB）、`preload-comet.so` 已 push
- 冒烟脚本：`release/_tmp/hub_retest_smoke.py`

### 建议下一刀（更新）

1. 对照成功过的 `comet@0705` so / `target.h` 偏移，核对 `phys_offset`/`slide`/`delta=0` 是否异常  
2. 评估加长 LD_PRELOAD timeout（当前 90s）或调整 pipe stage 策略后单机复验  
3. 可选：补 favicon；README 标题改 OneRoot（仍为 P3）
