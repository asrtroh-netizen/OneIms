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

## 2. 缺口状态（2026-07-30 刷新）

> **同日开机韧性已落地**：详见 `docs/changes/2026-07-29-onekuku-v15-boot-alignment.md`。  
> **专攻入口**：`docs/architecture/2026-07-30-onekuku-focus-war-map.md`。  
> 下表「现状」以当前源码为准；勿再按旧版「无」开新工。

| 优先级 | 能力 | 工作量 | 风险 | V15 证据 | OneIMS 现状（2026-07-30） |
|---|---|---|---|---|---|
| — | `/proc/net/tcp*` 发现 adbd 端口 | L | 中 | `EnvironmentUtils.findAdbdListeningPorts` | ✅ `OneKukuAdbEnvironment.findAdbdListeningPorts` |
| — | 上次成功无线端口缓存 | M | 中 | `ShizukuSettings` + `AdbWirelessHelper` | ✅ last-port（同 boot-alignment） |
| — | mDNS 空窗 /proc 轮询 | M | 低 | `SelfStarterService` pollJob | ✅ `OneKukuEmbeddedAdbActivator` |
| — | `WifiReadyMonitor` | M | 低 | `WifiReadyMonitor.kt` | ✅ `OneKukuWifiReadyMonitor` |
| — | `USER_PRESENT` 0/5/15s | M | 低 | `UserPresentRestartReceiver` | ✅ `OneKukuUserPresentRestartReceiver` |
| — | 可选 Watchdog | L | 中 | `WatchdogService.kt` | ✅ `OneKukuWatchdogService`（可配） |
| — | 通知监听自动填六位码 | M | 中 | `AdbPairingNotificationListener` | ✅ `OneKukuPairingCodeListener`（默认关） |
| — | tcpip 端口可配置 | S | 低 | `TCPIP_PORT` 设置 | ✅ `persistTcpipPort` |
| P2 | `WirelessBootStartWorker` WorkManager 退避 | L | 中 | `WirelessBootStartWorker.kt` | 仍并在 IMS 开机协调器；按需再拆 |
| P3 | 纯通道 Headless Starter 与 IMS 恢复解耦 | M | 中 | `SelfStarterService` | 仍绑 `OneKukuBootRestoreCoordinator` |
| P0（验收） | 划掉/复连真机矩阵 | — | — | V15.1.0 秒醒 | 代码已合（reconnect shots / READY 优先 / wait-binder）；真机多为 **NOT RUN** |

---

## 3. 架构对照（一句话）

```
V15:    Manager App ──ADB──► shizuku_server(native) ──Binder──► 多 App Provider
OneKuku: OneIMS App ──ADB──► onebridge_server(app_process) ──Binder──► 本 App Provider
```

原理相同；协议面与开机韧性层不同。

---

## 4. 建议落地顺序（若继续专攻）

1. **真机验收**今日复连三连 + 冷启韧性（最大证据缺口）  
2. **复连差分**对照外置 V15.1.0（仍假死时再动刀）  
3. **可选**：WorkManager 退避 / Headless Starter 与 IMS 解耦（P2/P3）  
4. **不要**扩协议面到完整 Shizuku

---

## 5. 证据来源

- OneIMS：`app/src/onekuku/**`、`bridge/**`、上表设计/架构文档  
- V15：`manager/.../adb/*`、`starter/SelfStarterService.kt`、`receiver/*`、`watchdog/WatchdogService.kt`、`utils/EnvironmentUtils.kt`  
- 本轮为静态代码对照；真机矩阵 **NOT RUN**
