# OneKuku ↔ Shizuku V15 开机韧性全对齐

**日期**：2026-07-29  
**范围**：`onekuku` 通道；不扩多 App / 完整 IShizukuService（立项闸门不变）

## 落地项

| 项 | 实现 |
|---|---|
| `/proc/net/tcp*` 端口发现 + last-port | `OneKukuAdbEnvironment` |
| mDNS 空窗 /proc 轮询 | `OneKukuEmbeddedAdbActivator` |
| WifiReadyMonitor | `OneKukuWifiReadyMonitor` |
| USER_PRESENT 0/5/15s | `OneKukuUserPresentRestartReceiver` |
| Watchdog（默认开） | `OneKukuWatchdogService` + `ConfigStore.isOneKukuWatchdogEnabled` |
| 自动抓六位码（默认关） | `OneKukuPairingCodeListener` + `ConfigStore.isOneKukuAutoPairingEnabled` |
| tcpip 口可配置 + start.sh Phase4 | `persistTcpipPort` / `bridge/assets/start.sh` |

## 刻意不对齐

多 App 授权、UserService、`newProcess`、独立 Manager APK。

## 验证

- 双 flavor Kotlin 编译
- 真机冷启 / 解锁 / binder 死 → NOT RUN
