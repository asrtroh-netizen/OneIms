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

---

## 残留排查（用户怀疑 App 残留 · 同夜）

截图：`Android System Intelligence` 屡次停止（`com.google.android.as`）。

| 嫌疑 | 结论 | 证据 |
|---|---|---|
| OneRoot App `com.oneroot.app` | **不成立**（未安装） | `pm list packages` 空；此前 20:22 已卸 |
| RootMyPixel `com.alex193a.rootmypixel` | **不成立** | 包不存在 |
| 系统智能 ASI 弹窗 | **真有崩溃弹窗**，与 temp-root FAIL **弱相关** | 已 `am force-stop`；属 Google 组件 |
| `com.google.android.carrier` | **强相关系统异常** | 曾 `installed=false` → `vendorprovider` 找不到 → `com.google.android.apps.scone` ModemService 崩 |
| `/data/local/tmp` 临时 Root 文件 | 部分清理 | shell 已删 `preload-comet.so`/`exploit.log`/`su_daemon.log` |
| OneIMS carrier 暂存（root 属主） | **仍在、shell 删不掉** | `oneims-carrierconfig-staged/*.xml`、`telephony.db`、`t.db` |

处置：`cmd package install-existing com.google.android.carrier` → `installed=true`，`pm path` 恢复为 `CarrierSettings.apk`；provider 查询从抛异常变为可解析（空结果）。

对「一键 Root 仍无 uid=0」：App 残留假说 **否证**；carrier 包缺失会制造系统噪声，但 LD_PRELOAD `pipe stage` 超时仍应优先按 exploit/偏移查。有 uid=0 后再清 root 属主 carrier 暂存。

### 对照实跑（carrier 恢复后）

前提：`com.google.android.carrier` `installed=true` · Hub `:51628` · 同机 `comet@0705`。

| 项 | 重启后（carrier 缺失前状态已清僵尸） | carrier 恢复后 |
|---|---|---|
| dry-run | code=0 | code=0 |
| 实跑 job | **code=1** · 2 轮无 uid=0 | **仍 code=1** · 同失败文案 |
| ld_preload | rc=124 / 约 90s | rc=124 / 约 90s |
| su | 无 | 无 |

结论：**carrier 恢复不能让一键 Root 变好** → 提权失败与 carrier 包无关；下一刀继续 exploit（`delta=0` / pipe stage / timeout）。  
证据：`release/_tmp/hub_retest_after_carrier_{pre,final}.json` · `hub_retest_after_carrier_job.log`

### 再重启复测（用户确认软件+so 曾成功）

动作：`adb reboot` → boot_completed=1 → Hub 实跑。

| 项 | 结果 |
|---|---|
| 第 1 轮 LD_PRELOAD | **rc=0**（未再 124 超时），但仍 `verify su: not uid=0`；日志停在 `pipe stage attempt=1/72` |
| 第 2 轮 | `adb.exe: no devices/emulators found`（设备中途掉线） |
| 回连后 `/proc/uptime` | **≈13s** → 实跑期间发生**二次重启**（疑似 exploit 触发崩溃/重启，而非单纯 USB 松线） |
| 总结果 | job **code=1** · 仍无 uid=0 |

解读：与「软件坏了」不符（链路能跑完一轮）；更像 **so/偏移在当前内核态下不稳或走偏导致重启**，与用户「软件+so 成功过」可同时成立（环境/镜像漂移或偶发）。  
证据：`release/_tmp/hub_retest_reboot2_{pre,final}.json` · `hub_retest_reboot2_job.log`

### so 哈希/体积差分（曾成功 vs 当前 Hub）

体积两边均为 **220728** 字节（同长）。哈希 **不一致**：

| 角色 | 路径 | SHA256 | 备注 |
|---|---|---|---|
| 清单真源 / 曾验证 | `OneSo-assets/SHA256SUMS` + `E:\Down\TEMP\preload-comet-cp2a-260705-006.so` + Hub `.cache` + Lite/UI 发包 | `e74cbc7d2e5a941691568500a24a77d92a6decd175e6b56090e99d047f847245` | 文档称原版/已验证 |
| **当前 Hub 实际用** | `OneSo-assets/so/CP2A.260705.006/…` + `app/src/main/assets/temproot/…`（mtime 21:52） | `64ed9d74b335704f6c8d94f94490d183bedebf1bc150e2f51cbd03838b8e4055` | 与 App 1.1.6 热补丁同哈希 |

字节差：同长度下 **仅 10 个字节不同**（首差偏移 `150620`…）。  
`SHA256SUMS` 仍登记 `e74cbc7d…`，但磁盘上 OneSo-assets 主文件已被换成 `64ed9d74…` → **清单漂移**。  
证据：`release/_tmp/so_hash_diff.json`；参见 `docs/changes/2026-08-05-oneroot-pc-lite-try-slide-drop.md`。

建议下一刀：用 `e74cbc7d…` 覆盖回 `OneSo-assets`（及 assets）后再跑一键 Root 对照。
