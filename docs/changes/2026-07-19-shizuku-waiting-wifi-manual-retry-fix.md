# Shizuku：Waiting for Wi‑Fi 必须手点「重试」修复

日期：2026-07-19  
工作树：`E:\GQ\One\_forks\HSSkyBoy-Shizuku-clean`  
制品：`E:\GQ\One\_forks\Shizuku.apk`

## 现象

开机后出现「Wireless ADB auto-start / Waiting for Wi‑Fi…」通知，必须手动点「重试」才能继续激活。

## 根因

1. 通知「重试」只是调用 `ShizukuReceiverStarter.startWireless(force=true)` 重新 enqueue Worker。
2. Worker 在 Wi‑Fi / TCP 端口未就绪时，原先约 **5 次 `Result.retry()` 后 `Result.failure()` 永久停死**。
3. 永久失败后 WorkManager 不再自动调度，只能手点「重试」重新入队。

## 修复

- `WirelessBootStartWorker`：Wi‑Fi 等待路径不再 permanent-fail；持续 `Result.retry()` + 退避。
- 每次 attempt 对 `validateThenEnableWirelessAdb(..., wait=true)`（约 20s STA 轮询）。
- `WifiReadyMonitor`：Wi‑Fi 就绪时走与「重试」相同的 `startWireless(force=true)`。
- enqueue 时确保注册 `WifiReadyMonitor`。

## 验证

- `:manager:assembleRelease`：`BUILD SUCCESSFUL`
- 真机冷启动 / 手点是否仍必需：待用户覆盖安装后验收（本机当时无 adb 设备）

## 非目标

- 不推送到 `HSSkyBoy/Shizuku` upstream
- 不改 OneIMS

---

## 追加：重试也无效 / 必须进 App 点启动（r7）

### 现象升级

通知「重试」无效，只有打开 App 手点「启动」才 Active。

### 根因

1. `SelfStarterService` 使用 `shortService` FGS：Android 14+ 易在无线调试 TLS 端口就绪前被系统掐死。
2. 开机路径过早走 mDNS，且失败/收尾可能关掉 `adb_wifi_enabled`，连接窗口被掐断。
3. 手点「启动」走 Activity / 发现对话框，进程与时机更稳，所以只有手动路径成功。

### 再修复

- `SelfStarterService` → `specialUse` FGS（对齐可用对照路径）
- 优先已知 TCP 端口；否则 mDNS + 最长约 45s 轮询
- 开机不主动 `DISABLE_WIRELESS_DEBUGGING_WHEN_FINISHED`
- 「重试」直接 `startForegroundService(SelfStarterService)` + 再 enqueue Worker

### 制品

`E:\GQ\One\_forks\Shizuku.apk`（`shizuku-v13.6.1-RC2.r7.*-release.apk`）

---

## 追加：adb 真机验收（2026-07-19 13:55，Pixel 9 Pro Fold）

### 环境

- 设备：`47111FDKD0009J` / Pixel 9 Pro Fold
- adb：`E:\GQ\One\_toolchain\android-sdk\platform-tools\adb.exe`
- 证据目录：`E:\GQ\One\_forks\_adb_verify\`

### 已做

| 步骤 | 结果 |
|---|---|
| 安装前版本 | `13.6.1-RC2.r3.8872fac`（versionCode=3） |
| `adb install -r Shizuku.apk` | **Success** |
| 安装后版本 | `13.6.1-RC2.r7.051cb7f`（versionCode=7） |
| `WRITE_SECURE_SETTINGS` | granted=true |
| 冷重启 | `sys.boot_completed=1`；`BootCompleteReceiver` 已拉起进程 |

### 冷启验收结论（FAIL）

| 观测 | 证据 |
|---|---|
| 通知仍 Waiting | `Wireless ADB auto-start` / `Waiting for Wi-Fi or a reachable TCP ADB port`（含「重试」「取消」） |
| Wi‑Fi 实际已连 | SSID `ChinaNet-NLAm-5G`，IP `192.168.1.56`，state COMPLETED |
| TLS 端口已监听 | `*:35051 LISTEN`；`adb_wifi_enabled=1` |
| 经典 tcp.port 属性空 | `service.adb.tcp.port` / `persist.adb.tcp.port` 为空 → `getStartableAdbPort()` 易判 null |
| SelfStarterService | `am start-foreground-service` → **Error: Not found**（全程 `isKeyguardShowing=true`） |
| Shizuku 服务 | dumpsys activity services → (nothing) |

### 根因线索（待解锁后复验）

1. Worker/enqueue 把「可启动端口」绑在经典 `adb.tcp.port` / 设置项上，**看不见 Pixel 无线调试 TLS:35051**，于是 Wi‑Fi 已就绪仍刷 Waiting。
2. 锁屏未解期间 `SelfStarterService` 显式启动失败（not found），「重试」同路径可能被挡；需解锁后再比一次。

### 未完成

- 用户解锁后的自动激活 / 手点「重试」对比 log：**BLOCKED**（验收窗口内始终锁屏）

---

## 追加：r8 真机修复（2026-07-19 14:14）

### 根因（三连）

1. **后台 Toast NPE**：`enableWirelessADB` 在 Worker 线程 `Toast.makeText` → 无 Looper，settings 已写入后仍抛错，打断 `WirelessBootStartWorker`。
2. **解锁门误用**：`KeyguardManager.isDeviceLocked` 在 Pixel Fold 上误判 → 卡「Waiting for unlock」并错过 `USER_PRESENT`。改为 `UserManager.isUserUnlocked`。
3. **TLS 端口盲区**：Pixel 无线调试端口（如 35051）不在 `service.adb.tcp.port`；增加 `/proc/net/tcp*`（uid 2000 LISTEN）发现。

### 制品

- `E:\GQ\One\_forks\Shizuku.apk`（`shizuku-v13.6.1-RC2.r8.9435979-release.apk`）
- commits：`9435979`、`5d0dacd`（HSSkyBoy-Shizuku-clean）

### 验证

| 场景 | 结果 |
|---|---|
| 覆盖安装 r8 | Success，`versionName=13.6.1-RC2.r8.9435979` |
| 打开 App + 通知「重试」/ 广播重试 | **PASS**：`shizuku_server` 起来，`Watchdog observed Shizuku binder received` |
| 冷启无操作 | **部分**：不再永久 Waiting；SelfStarter 会拉起；**binder 仍常需进 App/点重试**（TLS 端口冷启晚到 / 无头 mDNS 窗口） |
