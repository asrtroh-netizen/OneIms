# 冷开机真机验证（重启）

**日期**：2026-07-15  
**设备**：Pixel 9 Pro Fold `47111FDKD0009J`  
**包**：`com.oneims.app` 2.1.5 / 50（含重连加速）

## 操作

1. `adb reboot` → 等 `sys.boot_completed=1`  
2. `wm dismiss-keyguard` 解锁  
3. 采 `OneIMS-*` / ActivityManager FGS 日志

## 结果

| 项 | 证据 |
|---|---|
| 静默激活 | `boot: silent activate success ready=true detail=core_running_tcpip` |
| UI hint | `READY_SLEEPING` →（无快照）`NO_SNAPSHOT_SLEEPING` |
| binder | `OneBridge binder received` |
| 耗时（本轮） | BootReceiver 起进程 `17:13:52.719` → binder `17:14:05.140` ≈ **12.4s**；success `17:14:06.737` |
| `:5555` 快路径 | 重启后 **未命中**（tcpip 口不跨重启，预期）→ 走 mDNS `connect` 口 |

## 发现与修复

重启瞬间 `LOCKED_BOOT_COMPLETED` 曾抢先 `startForegroundService` → `Background started FGS: Disallowed`，随后 `BOOT_COMPLETED` 白名单才 Allowed。

**修复**：`BootReceiver` 在 `LOCKED_BOOT_COMPLETED` 只拉 `GuardService`，**不再** enqueue `OneKukuBootRestoreService`；恢复编排仅 `BOOT_COMPLETED` / `USER_UNLOCKED`。

## 对比

| 场景 | 体感 |
|---|---|
| 划掉后台（进程杀） | ≈1.2s（`:5555` 快路径） |
| 冷开机（本轮） | ≈12s 级静默就绪（无「等半天」） |
| 优化前 | 超时叠加可达数十秒～分钟 |
