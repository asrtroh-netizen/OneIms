# 开机自启三方对照：Boy / 我的(thedjchi) / 原版不更新 13.6

日期：2026-07-18

## 对象

| 代号 | 制品 / 源码 | 说明 |
|------|-------------|------|
| **原版不更新 13.6** | `OneIms-Lite-HSSkyBoy-v13.6.1-RC2.r2.424254b-release.apk`（提交 `424254b`） | 用户认可「自启很棒」；无 V15「检查更新」入口 |
| **Boy** | `_forks/HSSkyBoy-Shizuku`（V15 皮 + 开机栈） | 后续改过网络约束 / Direct Boot，曾出现冷启动失败 |
| **我的** | `_forks/thedjchi-Shizuku` → `shizuku-v13.7.x-asrtroh-*.apk` | View 系首页；`AdbStartWorker` + `WifiReadyMonitor` |

包名三者均为 `moe.shizuku.privileged.api`。

## 链路对照

| 维度 | 原版 13.6 (`424254b`) | Boy（改坏后 → 已回滚方向） | 我的 (thedjchi) |
|------|----------------------|---------------------------|-----------------|
| 开关真源 | `KEEP_START_ON_BOOT_WIRELESS` | 同左 | `KEY_START_ON_BOOT` + `canWirelessAutostart()`（配对密钥+WSS） |
| BOOT 入口 | `BootCompleteReceiver` → `startOnBoot` | 曾：LOCKED 只 arm、BOOT 额外常驻 arm USER_PRESENT | LOCKED arm；BOOT → `AdbStartWorker` + `WifiReadyMonitor` |
| Worker | `WirelessBootStartWorker` | 同左 | `AdbStartWorker` |
| 网络约束 | **`UNMETERED`**（等 Wi‑Fi） | 曾改 **`CONNECTED`**（蜂窝可抢跑耗尽重试）→ 已恢复 UNMETERED | `UNMETERED`（无 TCP 时） |
| API 门槛 | 无线开机 **Android 13+** | 曾放宽 11+ → 已恢复 13+ | Android 11+（R）或 TV/TCP |
| 开无线调试 | `AdbWirelessHelper.validateThenEnableWirelessAdb`（校验 Wi‑Fi transport） | 同左 | `Settings.Global.putInt(adb_wifi_enabled)` |
| 拉起服务 | `SelfStarterService` + `AdbWirelessHelper.startShizukuViaAdb` | 同左 | `SelfStarterService` + `AdbStarter` |
| 解屏续跑 | Worker 内 lock → arm `USER_PRESENT` | 曾在 BOOT 无条件 arm（已解锁时备份无效） | 同 Worker/锁态 arm |
| 更新 UI | **无**「检查更新」 | V15 有 Releases 入口 | 无同款；设置里外链 wiki/releases |
| 用户口碑 | 自启稳 | V15 冷启动失败 | 另栈；曾对齐 Boy 但仍非 13.6 原路径 |

## 根因摘要（为何 13.6 更稳）

1. **等 Wi‑Fi 用 UNMETERED**：系统在真正有非计费网络（通常 Wi‑Fi）前不跑 Worker；改成 CONNECTED 后，蜂窝先满足约束 → 无 Wi‑Fi transport → 重试耗尽 → 放弃。
2. **专用无线开机开关**：`KEEP_START_ON_BOOT_WIRELESS` 语义单一，不和「配对即可自启」隐式逻辑缠在一起。
3. **thedjchi 是另一套**：`WifiReadyMonitor` + `canWirelessAutostart` 更复杂，不是 13.6 金标的逐行复刻。

## 当前处置（Boy 工作区）

- 开机四件套已按 `424254b` 回滚（Starter / UserPresent / WirelessBootStartWorker）。
- `BootCompleteReceiver`：LOCKED_BOOT **仍跳过 WorkManager**（防 Direct Boot 崩溃），BOOT_COMPLETED 走与 13.6 相同的 `startOnBoot`。
- 版本号拟标 `V15.0.1`（皮保留；自启行为对齐 13.6）。

## 验证清单（人工）

1. 安装目标 APK，授予 `WRITE_SECURE_SETTINGS`。
2. 打开「开机时通过无线调试启动」类开关，手动成功启动一次并保持配对。
3. 关机再开机（完整冷启动），解屏后看 Shizuku 是否自动起来。
4. 若失败：解屏后立刻 `adb logcat -d | findstr /i "Shizuku WirelessBoot USER_PRESENT AdbStart"`。
