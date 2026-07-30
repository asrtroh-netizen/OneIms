# 2026-07-30 · Shizuku V15.1.0 最终保留能力清单（只读盘点）

**范围**：只查既有日志/文档/邻仓 README，不翻业务源码。  
**对象**：上传到 GitHub 的小米线包（本地 remote：`asrtroh-netizen/shizuku-dropin`；对外身份 `V15.1.0` / `151000`）。

## 终版公式（已冻结）

**Plus 底 + V15 皮 + 白名单 + V15 特性**

| 项 | 定稿 |
|---|---|
| 包名 | `moe.shizuku.privileged.api`（dropin） |
| 版本 | `V15.1.0` / `151000` |
| 入口 | `MainActivity` → `V15SkinHomeActivity` → LibrarySkin |
| 非目标 | Plus 原皮首页；不替代 Pixel 仓 `V15.0.0` |

证据：`E:\GQ\One\_forks\ShizukuDropIn-Local\README.LOCAL.md` §终版定义；`docs/changes/2026-07-30-shizuku-dropin-local-vs-v15.md` §终版冻结。

## 默认开 / 保留（oem4 用户点名白名单）

### 1. 一类精华

- A16/17 兼容
- Watchdog
- Service Doctor
- Force WADB（强制无线 ADB）
- QS Tile
- Drop-In（官方包名兼容）
- 批量授权
- 应用内更新

### 2. 兼容层

- SU Bridge
- Local ADB Proxy（15555）
- Shell Interceptor
- Root Compatibility Hub
- Samsung UID 1000

### 3. Plus API

- AICore+（Master/Experimental/NPU 仍默认 OFF）
- Device Spoofing
- Window Manager Plus
- Overlay Manager Plus
- Network/DNS Governor
- Continuity Bridge

## 一并保留的 V15 冷启 / 热路径特性

- Direct Boot 分流（`LOCKED_BOOT_COMPLETED` 只武装解锁重试）
- `UserPresentRestartReceiver`（解锁后 0s / 5s / 15s）
- `WifiReadyMonitor`
- root `su -c` 兜底
- `SelfStarterService` 热路径 FGS
- `WirelessBootStartWorker`（软备份 + TLS 端口发现）
- TCP 优先、无 Wi‑Fi 再起
- 闪退相关修复（文档记载）

## UI / 首页默认（oem2～oem3）

| 项 | 默认 |
|---|---|
| 无线 ADB 启动卡 | 显示（保留） |
| 授权 / 开机自启 / Watchdog | 保留 |
| 自动化入口卡 | 显示 |
| 终端 / 了解更多 / 活动日志 | 隐藏 |
| 已关 Plus 卡 | 隐藏（`hide_disabled_plus_features` 默认开） |
| 主色 / Hero | `#0B57D0` / `#A9C7FF`（对齐 V15） |

## 明确不默认开（代码可在，默认不打扰）

Dhizuku、AVF、Storage Proxy、Activity Manager Plus、NPU/实验 AI、Binder Firewall/Shadow、各类 root ghosting / bootloader 实验项等。

## 证据与缺口

| 检查 | 结果 |
|---|---|
| README.LOCAL oem4 / 终版冻结 | PASS（本轮 Read） |
| OneIMS 对照文档 `2026-07-30-shizuku-dropin-local-vs-v15.md` | PASS（本轮 Read） |
| 本地 git remote → `asrtroh-netizen/shizuku-dropin` | PASS（`git remote -v`） |
| `gh release list` 远端 release 资产 | **NOT RUN / 404**：当前 `gh` 凭据下仓库不可见（私有或账号无权）；以本地文档与 remote URL 为准 |
| 源码逐文件核对默认布尔 | **未做**（用户明确要求不翻源码） |
