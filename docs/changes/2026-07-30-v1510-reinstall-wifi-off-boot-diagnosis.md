# 2026-07-30 · V15.1.0 重装后「关 WiFi 冷启」失效诊断

**现象**：重装 Drop-In `V15.1.0` / `151000` 后，无法再「重启前关掉 WiFi → 冷启仍自动起服务」。

**结论（一句话）**：不是 15.1.0 删了该能力；「无 Wi‑Fi 再起」= **已有可连 adbd TCP/TLS 端口时跳过 WiFi 等待**。重装清空开机自启 / `mode` / 配对后，冷启退回「必须 STA 开无线调试」，再叠加小米自启动拦截，关 WiFi 就起不来。

## 契约澄清

| 文档说法 | 源码真实含义 |
|---|---|
| README / 保留清单：「TCP 优先、无 Wi‑Fi 再起」 | 仅当 `getStartableAdbPort()!=null` 才 early-return，不靠 WiFi |
| 用户口语：「关 WiFi 也能冷启」 | **无条件免 WiFi** —— 当前实现**不保证** |

证据：

- `SelfStarterService.kt:115-122`：`Using known ADB TCP port (no Wi‑Fi wait)`
- `AdbWirelessHelper.getStartableAdbPort()`：系统 `service.adb.tcp.port` / TCP 模式配置口 / TLS 无线口 / lastPort 且端口在听
- `EnvironmentUtils.isWifiRequired()`：`tcp.port<=0 || !getTcpMode()` → true 时视为需要 WiFi
- `AdbWirelessHelper.validateThenEnableWirelessAdb`：无端口时 **必须** `hasWifiReady`（STA）
- `WifiReadyMonitor`：只听 `TRANSPORT_WIFI`，关 WiFi 永不回调

## 重装后为何断链

| 闸门 | 重装影响 | 证据 |
|---|---|---|
| 无线开机自启 prefs | 默认 `false` | `LibrarySkinHome.kt:126-129` |
| `getStartOnBoot` | 只认 `COMPONENT_ENABLED_STATE_ENABLED`，DEFAULT≠开 | `ShizukuSettings.java:368-371` |
| `mode` | 默认 `UNKNOWN` → Background start not supported | `ShizukuSettings.java:301-302` |
| 配对密钥 / lastPort | SharedPreferences 清空 | `PreferenceAdbKeyStore` / `getLastPort` |
| `WRITE_SECURE_SETTINGS` | 需重授 | Worker / 写 `adb_wifi_enabled` |
| MIUI WakePath | 广播 SKIPPED（既有 FAIL） | `docs/changes/2026-07-30-shizuku-dropin-local-vs-v15.md` |

**已排除**：V15.1.0 删除「无 WiFi」分支（oem6 SelfStarter early TCP 仍在）。

## 用户侧最短恢复（关 WiFi 冷启想再用）

1. 安全中心 → 自启动放行 `moe.shizuku.privileged.api`
2. App 内重新打开「无线开机自启」
3. 重授 `WRITE_SECURE_SETTINGS` 并重新配对一次（让 `mode=ADB`）
4. **先成功起一次服务**，并确认经典 TCP 监听：`service.adb.tcp.port` / `persist.adb.tcp.port` > 0 且端口在听
5. 再测「关 WiFi → 重启 → 冷启」；若无 TCP 端口，预期就是起不来（契约如此）

Root 机且 `mode=ROOT`：可走 `su -c`，与 WiFi 无关。

## 工程债（可选后续）

1. `getStartOnBoot` 对齐 Manifest DEFAULT（与 `isComponentEnabled` 一致）
2. 文档改成「有 TCP 端口才免 WiFi」，避免预期漂移
3. 冷启日志结构化：`mode` / `startablePort` / `getStartOnBoot` / `hasWifi` / WakePath

## 本轮验证

| 项 | 结果 |
|---|---|
| 源码/文档交叉核对 | PASS |
| 真机 `adb` / logcat / getprop | **NOT RUN**（本机 PATH 无 `adb`；需人工按上表采证） |
| 代码修改 | 无（本轮诊断-only） |
