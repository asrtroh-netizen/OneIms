# OneKuku / OneBridge vs Shizuku V15.0.0 — 对齐全景

**日期**：2026-07-29  
**范围**：内部通道（`onekuku` + `bridge`）对照 `_forks/HSSkyBoy-Shizuku-clean`  
**结论摘要**：关键冷启/配对/binder 成功门禁已对齐；**开机基础设施**（端口发现、Wi‑Fi/解锁重试、Watchdog、自动配对码）已于同日落地，见 `docs/changes/2026-07-29-onekuku-v15-boot-alignment.md`。不做多 App / 完整 API 面。

---

## 0. 产品边界（刻意不分叉对齐）

| 维度 | OneBridge | Shizuku V15 |
|---|---|---|
| 形态 | 嵌在 OneIMS 内的「启动通道」 | 独立 Manager + 多 App SDK |
| 客户端 | 仅 `com.oneims.app` | 任意授权 App |
| API 面 | 生命周期 + 少量 system binder 包装 | 完整 `IShizukuService`（进程/用户服务/rish…） |
| 立项闸门 | `docs/design/2026-07-15-onebridge-privilege-min.md` | — |

**不要对齐**：多 App 授权商店、`newProcess`、UserService、换皮第二商店叙事。

---

## 1. 已对齐（关键路径）

| 能力 | OneIMS | V15 参照 |
|---|---|---|
| mDNS pairing/connect | `OneKukuAdbMdns` | `AdbMdns` |
| 配对 RemoteInput + specialUse FGS | `OneKukuPairingHostService` / `OneKukuPairingNotification` | `AdbPairingService` |
| binder 就绪才算 Success + 最多 3 次重试 | `OneKukuEmbeddedAdbActivator` | `AdbWirelessHelper.startShizukuViaAdb` |
| 已在跑不默认 pkill | `OneKukuCoreComponent.bridgeBootShellCommand` | SelfStarter skip if ping |
| Provider 去重 living binder | `BridgeBinderProvider` | `ShizukuProvider` |
| Client 启停时重投 binder | `BridgeService.ClientBinderSender` | `BinderSender` |
| 弃用 App 侧常驻轮询 FGS | `OneKukuResidentService.start()` no-op | Manager 不 resident-poll |
| Hero 不强调休眠 | 三态 INACTIVE/ACTIVATING/READY | V15 首页 inactive/ready |

---

## 2. 未对齐 / 部分对齐（仍有颗粒度）

| 优先级 | 缺口 | 工作量 | 风险 | V15 证据 | OneIMS 现状 |
|---|---|---|---|---|---|
| P0 | `/proc/net/tcp*` 发现 adbd TLS 监听端口 | L | 中 | `EnvironmentUtils.findAdbdListeningPorts` | **无** |
| P0 | 上次成功无线端口缓存 + 失效清理 | M | 中 | `ShizukuSettings` + `AdbWirelessHelper` | **无** |
| P1 | mDNS 等待期端口轮询（~400ms） | M | 低 | `SelfStarterService` pollJob | 仅 discover 超时 |
| P1 | `WifiReadyMonitor` NetworkCallback | M | 低 | `WifiReadyMonitor.kt` | BootReceiver 条件监听偏弱 |
| P1 | `USER_PRESENT` 0/5/15s 强制重试 | M | 低 | `UserPresentRestartReceiver` | **无**专用接收器 |
| P2 | `WirelessBootStartWorker` WorkManager 退避 | L | 中 | `WirelessBootStartWorker.kt` | IMS 开机协调器合并路径 |
| P2 | 可选 `WatchdogService`（binder 死→限次重启） | L | 中 | `WatchdogService.kt` | **无** |
| P2 | 通知监听器自动填六位码 | M | 中 | `AdbPairingNotificationListener` | **无**（需用户授权） |
| P3 | 纯通道 Headless Starter 与 IMS 恢复解耦 | M | 中 | `SelfStarterService` | 绑在 `OneKukuBootRestoreCoordinator` |
| P3 | tcpip 端口可配置（非写死 5555） | S | 低 | `TCPIP_PORT` 设置 | `PERSIST_PORT = 5555` |
| P3 | `bridge/assets/start.sh` 与 Phase4 同步 | S | 低 | `Starter.kt` | 可能仍 unconditional pkill |

---

## 3. 架构对照（一句话）

```
V15:    Manager App ──ADB──► shizuku_server(native) ──Binder──► 多 App Provider
OneKuku: OneIMS App ──ADB──► onebridge_server(app_process) ──Binder──► 本 App Provider
```

原理相同；协议面与开机韧性层不同。

---

## 4. 建议落地顺序（若继续对齐）

1. **端口发现 + last-port 缓存**（最大开机收益，对齐 V15 冷启经验）  
2. **Wi‑Fi 就绪监听 + USER_PRESENT 重试**（少点手动「重试」）  
3. **可选 Watchdog**（后台静默复活，默认关/可开关）  
4. **自动读配对码**（便利项，权限摩擦大，可后置）  
5. **不要**扩协议面到完整 Shizuku

---

## 5. 证据来源

- OneIMS：`app/src/onekuku/**`、`bridge/**`、上表设计/架构文档  
- V15：`manager/.../adb/*`、`starter/SelfStarterService.kt`、`receiver/*`、`watchdog/WatchdogService.kt`、`utils/EnvironmentUtils.kt`  
- 本轮为静态代码对照；真机矩阵 **NOT RUN**
