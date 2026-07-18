# 2026-07-18 · Shizuku 解锁 / Direct-boot 状态

## 问题

冷启动第一枪 `ACTION_LOCKED_BOOT_COMPLETED` 时调用 WorkManager，credential-encrypted 存储未解锁 →  
`WorkManager is not initialized properly` 进程崩溃。

## 修复（已落地）

- 提交：`f06512c`（`thedjchi-Shizuku`）
- `LOCKED_BOOT`：只 `arm USER_PRESENT`，**不碰** WorkManager
- 解锁后：`UserPresentRestartReceiver` → `AdbStartWorker.enqueue`
- `ShizukuApplication`：未解锁同样只挂 USER_PRESENT

## 装机

- APK 含字符串 `locked boot: arm USER_PRESENT`（dex 已核）
- 2026-07-18 ~19:14 `adb install -r` Success

## 验证缺口

- 修后二次冷启动 / 解锁自启：**NOT RUN**（装完未再 reboot；现设备离线）
