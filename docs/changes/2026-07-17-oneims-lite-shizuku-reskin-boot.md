# OneIms Lite（thedjchi/Shizuku）换皮 + 开机自启修缮

工作目录：`E:\GQ\One\_forks\thedjchi-Shizuku`（独立于 OneIMS 主仓）

## 需求

1. 对齐 OneKuku：开机后若已配对过、且当前仍有 Wi‑Fi STA，则直接自启（勿干等）。
2. 保留 TcpIp。
3. UI/配色/状态卡对齐 OneIMS / OneIms Lite 审美后重新编译。

## 根因（开机）

`AdbStartWorker.enqueue` 在需要 Wi‑Fi 时一律加 WorkManager `UNMETERED` 约束；开机时 Wi‑Fi **已连上**时，部分 OEM 会卡住不调度。

## 改动摘要

| 项 | 说明 |
|---|---|
| `EnvironmentUtils.isWifiClientConnected` | 检测当前 Wi‑Fi STA |
| `AdbStartWorker.enqueue` | 已连 Wi‑Fi 则不加 UNMETERED，立即调度 |
| 配色 | `#0B57D0` / `#A9C7FF` |
| 卡片 | 圆角 20dp、内边距 20dp；状态卡 minHeight 72dp |
| 显示名 | `OneIms Lite`（包名仍为上游 `moe.shizuku.manager`） |
| Tcp mode | 保留 |

## 产物

- Debug APK：`E:\GQ\One\_forks\OneIms-Lite-debug.apk`
- 变更说明（分叉内）：`docs/ONEIMS-LITE-CHANGES.md`

## 验证

- `:manager:assembleDebug` **PASS**（本机 JDK 21 + SDK 36 + NDK 27）
- 开机自启实机：**NOT RUN**（需冷重启 + 旧 Wi‑Fi 已连）
