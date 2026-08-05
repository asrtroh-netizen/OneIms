# OneRoot 失败采证：mode0/configfs → kernel_panic

日期：2026-08-05  
设备：Pixel 9 Pro Fold (`comet`) `47111FDKD0009J` / `CP2A.260705.006`  
App：`com.oneroot.app` **1.1.4** (`versionCode=15`)

## 结论

用户可见「失败」的直接原因是：**exploit 进入 `prepare_kernel_page mode=0` 后在 configfs R/W 环中空转，随后内核 oops 触发 `kernel_panic` 重启**。  
不是匹配失败、不是 Shizuku 激活转圈、也不是本轮的 `ETXTBSY` 写盘问题。

## 证据

| 检查项 | 结果 |
|---|---|
| `sys.boot.reason` / `sys.boot.reason.last` | `kernel_panic` / `kernel_panic,oops:_fatal_exception` |
| 重启后 `uptime` | `up 0 min`（刚从 panic 起来） |
| 成功标记 `done=1` / `root=1` / `temp_su.sock` | 无 |
| 历史 `\.tmp-exploit-fail.log` | 564736 B；`configfs` 匹配约 **6618** 次；`done=1` = **0** |
| `\.tmp-exploit-114-run.log` | `slide-kaslr-ok` → 立刻 `prepare_kernel_page … mode=0` 后中断 |
| 重启后设备上 `/data/local/tmp/exploit.log` | 1646 B，内容基本为空（被重启截断/未写完） |
| `pstore` / `last_kmsg` | shell 无权限 / 无文件（未拿到 oops 栈细节） |
| 当前进程 | 无 `cve-2026*`；UI 在 Launcher；App 仍装着 1.1.4 |

## 失败链（复现形态）

`InstallActivity` → payload 加载 → `mode=1` → `slide-kaslr-ok` → **`mode=0`** → **configfs 读写刷屏** →（卡住或）**kernel_panic 重启**

## 归属

| 层 | 状态 |
|---|---|
| App 匹配 / 启动 | 正常 |
| App 重试写盘 ETXTBSY（1.1.3+） | 本轮未见复发 |
| App stall/取消 | 对 panic 无效（整机先死） |
| **so / mode0 / configfs 路径** | **根因区；成功率与稳定性问题在此** |

## 建议

1. **先停手**：不要连续点「一键临时 Root」，会抬高 panic 概率。  
2. 重启后若仍有残留：`pkill`/`rm` `/data/local/tmp/cve-2026*`（无 root 时用 shell 能力清理可写部分）。  
3. 下一刀应在 **so：`comet-CP2A.260705.006` 的 mode0/configfs**，App 侧改不动成功率。  
4. App 可选加固（次要）：Binder 死后强制 Failed；configfs 刷屏不计入“有进度”；发布前收回 `InstallActivity` exported。
