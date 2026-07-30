# 2026-07-30 · Pixel + V15.1.0：关 WiFi 冷启失效（纠正）与 persist TCP 修复

## 纠正语境

| 项 | 先前误判 | 用户纠正后 |
|---|---|---|
| 机型 | 小米 + MIUI WakePath | **Pixel**（历史装机：`47111FDKD0009J` Fold；装过 `shizuku-vV15.1.0-release.apk`） |
| 配对态 | 重装后未配对 | **已练过一次 WiFi**（无线调试配对/启动已成功） |

本机当轮 USB 连着的是 `22061218C`（小米 Fold），**不是** Pixel → Pixel 真机关 WiFi 冷启复测 **NOT RUN**。

## 根因（Pixel 线）

「练过一次 WiFi」只证明 **TLS 无线调试路径**通了；关 WiFi 冷启依赖的是 **经典 adbd TCP 口跨重启仍在听**。

| 环节 | 事实 |
|---|---|
| Pixel 重启 | 常清掉 `adb_wifi_enabled`（见 `2026-07-15-boot-adb-wifi-secure-settings.md`） |
| 普通启动成功路径 | 只跑 AdbClient `tcpip:PORT`，**不写** `persist.adb.tcp.port` |
| 已有 persist API | `AdbProxyService.enableAdbTcp` 会写 persist，但日常「启动」不走它 |
| 关 WiFi 冷启 | 无 WiFi → 开不了无线调试 → 无 TLS 口 → 无 persist TCP → SelfStarter early path 失败 |

所以：**不是没配对，是「练 WiFi ≠ TCP 口常驻」。**

## 代码修复（邻仓 DropIn V15.1.0）

仓：`E:\GQ\One\_forks\ShizukuDropIn-Local`

1. `AdbProxyService.persistAdbTcpPort(port)`：只 `setprop` service/persist，**不** `ctl.restart adbd`
2. `AdbWirelessHelper.startShizukuViaAdb`：binder ready 后若 TCP 模式开 → persist；并修正 `setLastPort` 用有效口
3. `AdbStarter`：前台启动成功后同样 persist

## 用户验收（Pixel）

1. 装含本修复的 V15.1.0 → **开着 WiFi** 再成功启动一次  
2. 查：`getprop persist.adb.tcp.port` 与 `service.adb.tcp.port` 应为配置口（默认 5555）  
3. 关 WiFi → 重启 → 应见日志 `Using known ADB TCP port (no Wi‑Fi wait)` 且 `shizuku_server` 起来  

临时绕过（未装修复包）：在 Shizuku 已 Active 时，用已有「Local ADB Proxy / enableAdbTcp」或 adb shell 手动：

```text
setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
```

（需已有 shell 特权；不要只依赖再练一次 WiFi。）

## 验证

| 项 | 结果 |
|---|---|
| 源码改动 | DropIn 三文件（见上） |
| Pixel 真机关 WiFi 冷启 | **NOT RUN**（本轮无 Pixel USB） |
| 构建 APK | 见交付命令摘要 |

## GitHub Release 覆盖（2026-07-30 22:57）

| 项 | 值 |
|---|---|
| 本地源包 | `E:\GQ\One\_forks\shizuku-vV15.1.0-persist-tcp-release.apk`（22:42 编译，6397198 bytes） |
| SHA256 | `A63D7905DD0890795A79A7A27AB015141588E52F36CC0B07C950E9E3928BACBA` |
| 发布仓/标签 | `asrtroh-netizen/shizuku` · `V15.1.0` |
| 资产名 | `shizuku-V15.1.0-release.apk` |
| 操作 | `gh release upload V15.1.0 … --clobber` → exit 0 |
| 远端元数据 | size=`6397198`，`updated_at=2026-07-30T14:57:27Z` |
| 下载 | https://github.com/asrtroh-netizen/shizuku/releases/download/V15.1.0/shizuku-V15.1.0-release.apk |
