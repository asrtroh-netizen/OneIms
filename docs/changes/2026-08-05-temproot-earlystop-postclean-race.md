# 临时 Root：截图「2 轮失败」与成功后立刻丢 uid=0

## 现象（Hub 截图）

- 设备 `comet/CP2A.260705.006`，so 命中本机 OneSo-assets
- 进度 100%：**失败：2 轮后仍无 uid=0**
- 日志可见 exploit 曾跑起来（`enforce=0` / preload starting）

## 根因（本轮证据）

1. **僵尸 su 漏检**  
   `su: connect daemon: No such file or directory` + root 属主 `/data/local/tmp/su`  
   旧 `looks_like_stale_su_daemon` 只认 `Permission denied` → 体检显示「未检测到」，清理还报 `ok=True`。

2. **成功后自毁（主因之一）**  
   某次本机复现：`early-stop: su uid=0 at 77s` → 打出 `SUCCESS`，但马上  
   `post-clean` 调用 `cleanup_temp_root_residuals` → 内部 `KILL_STUCK` **曾 `rm` sock/su**，把刚起来的 daemon 弄死 → 现场 `su` 立刻失败。  
   用户侧表现为「刚成功 / 或进度满格却仍无 uid=0」。

3. **early-stop 过早 kill**  
   daemon 有时还挂在 exploit 进程树下，立刻 `proc.kill()` 会带走 daemon。

4. **exploit 本身也有波动**  
   可见 `KernelSnitch mm_struct leak failed`；默认 2×90s 不够时会真失败。设备中途掉线会直接 `adb: no devices`。

## 修复

- `detect_stale` / cleanup：识别 `No such file` / 僵尸 su；清理失败不再假成功  
- `KILL_STUCK`：**只杀进程，绝不 rm su/sock**  
- 成功收尾：只 `KILL_STUCK`，并复验 uid=0；丢了则返回失败  
- early-stop：等待 4s 脱离 → 杀进程 → 再验；不稳定则不当成功  

## 验证

- 修前：SUCCESS 后 `AFTER probe` 立刻 `connect daemon: No such file`  
- 修后完整真机闭环：**NOT RUN**（本轮末 adb 无设备）；请重插线后再点一键  
