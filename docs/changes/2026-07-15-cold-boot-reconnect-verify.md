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

## 复验（FGS 拒绝归零）

**结论：PASS**（2026-07-15 17:28，同一 Pixel）

| 检查 | 结果 |
|---|---|
| `Background started FGS: Disallowed`（OneKukuBootRestore） | **0** |
| `Background started FGS: Allowed` + `code:BOOT_COMPLETED` | **1** |
| App 日志 | `boot action=android.intent.action.BOOT_COMPLETED enqueue restore debounce=1000` |

### 根因修正（相对首轮「只跳过 LOCKED_BOOT」）

1. `USER_UNLOCKED` 无白名单却会 `startFGS` → 改为只记日志，等 `BOOT_COMPLETED`  
2. Wi‑Fi `STATE_CHANGE` 在「未 attempted」时也会抢启 → 仅 `WAITING_WIFI` 才续跑  
3. 移除 `SIM_STATE_CHANGED` 广播调度（SIM 等待改 Coordinator 内完成）

### 注意

- 卸载重装会使应用进入 `stopped`，在首次用户打开前 **不会** 收 `BOOT_COMPLETED`；复验前需先冷启动一次 App。  
- 本轮因重装丢失 `has_paired_once` 标记/会话，hint 可能落到 `NEEDS_ACTIVATION`（与 FGS 门禁无关）。
