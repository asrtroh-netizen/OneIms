# 变更说明 · Battery Guru 2.5.0.5 反编译取料（研究用）

**日期**：2026-07-26  
**样本**：`com.paget96.batteryguru` 2.5.0.5  
**来源**：`Merennor/battery-guru-releases`（GitHub Release，研究副本）  
**SHA-256**：`1C50D002011DAB50E659442DF366F6E8438B45E021304B006290952FD14B8163`  
**工具**：jadx 1.5.5（exit 1，56 errors，产物可用）+ apktool 3.0.2（exit 0）  
**工作区（勿提交）**：`.tmp_batteryguru_re/`

## 纪律

- **取结构 / 取能力清单 / 取公开字符串语义**，再干净室回灌到 `:onebattery`
- **不**整包换皮、不复用商标/资源包、不拷闭源算法原文进产品仓
- 用户本轮口头「要反编译拿一些东西再造」覆盖种子文档「不做反编译」——仅限研究取料

## 架构骨架（从 Manifest 坐实）

| 组件 | 类名 | 用途 |
|---|---|---|
| Application | `application.BatteryGuruApplication` | Hilt/DI 入口（R8 扁平包名） |
| 主 UI | `activities.MainActivity` | 单 Task 主界面 |
| 核心 FGS | `services.BatteryInfoService` | **独立进程** `:battery_service`，`specialUse` FGS |
| AOD | `AODOverlayActivity` + `aod.NotificationService` | 锁屏全屏 AOD + NotificationListener 喂图标 |
| Widget×2 | `BatteryInfoWidget` / `BatteryInfoWidgetWithClock` | 基础 / 带时钟 |
| QS Tile | `BatteryTileService` | 快捷设置磁贴 |
| BLE | `BluetoothLeService` | 蓝牙设备电量 |
| Workers | `AnomalyAlertWorker` / `ChargingSummaryWorker` / `OvernightReportWorker` / `SummaryNotificationWorker` / `StartBatteryInfoServiceWorker` / `StreakMilestoneWorker` | 异常耗电、充电摘要、隔夜报告、常驻通知、保活拉起 |
| Boot | `BootReceiver` + `PackageReplacedReceiver` + `PowerDisconnectedReceiver` | 开机/升级/拔电 |

关键权限（相对 OneBattery 增量）：

- `SYSTEM_ALERT_WINDOW`（悬浮层）
- `BIND_NOTIFICATION_LISTENER_SERVICE`（AOD 通知图标）
- `BATTERY_STATS` / `DUMP`（特权侧，Play 安装通常拿不到完整）
- `PACKAGE_USAGE_STATS`（分应用，OneBattery 已有）
- Billing / Ads（产品侧 OneBattery **不跟**）

## 混淆现状

- 业务逻辑大量在 `defpackage/*`（~9900 Java）
- 清晰保留名集中在 `com.paget96.batteryguru.{aod,widgets,work,receivers,services,utils.*}`
- Native：`libCrashGuard.so`（崩溃库）、`libStatistics.so`（仅 armeabi-v7a）——**核心电量逻辑主要在 Java/Kotlin 层**，非重 so 壳

## 取料 → OneBattery 再造映射

| 取到的点子 | OneBattery 落地建议 | 分期 |
|---|---|---|
| 功率文案 `watt` / `watts_formatted` | UI 增加 `W = V×I/1000` | P0 |
| 多闹钟 + `alarm_frequency` | 低电 / 高温 / 低温闹钟 + 频率 | P0 |
| 常驻通知优先级三档（default/high/minimal） | `BatteryInfoService` 式常驻通知（可关） | P0 |
| 充电器档案 `charger_profiles_*` | 按典型功率聚类充电会话 | P1 |
| 异常耗电 `AnomalyAlertWorker` | WorkManager 对比基线放电速率告警 | P1 |
| Overlay `SYSTEM_ALERT_WINDOW` | 可拖拽悬浮电量条 | P1 |
| 第二 Widget（带时钟） | Glance/RemoteViews 变体 | P1 |
| QS Tile | `TileService` 显示电量/温度 | P1 |
| AOD = OverlayActivity + NotificationListener | **仍 OUT 首期**；若做，走自有实现勿抄资源 | P2/OUT |
| 独立进程 `:battery_service` | 评估是否隔离 FGS（收益 vs 复杂度） | 架构备选 |
| 服务器 ingest 模型（Charging/Discharging/Device） | **不抄云协议**；本地 Room schema 可对照字段语义 | 参考 |

## 验证

```text
# 样本
Get-FileHash .tmp_batteryguru_re\work\battery-guru-2.5.0.5.apk -Algorithm SHA256
# → 1C50D002011DAB50E659442DF366F6E8438B45E021304B006290952FD14B8163

jadx -d .tmp_batteryguru_re\jadx_out --show-bad-code --no-res <apk>
# → finished with errors, count: 56（可接受）

apktool d -f -o .tmp_batteryguru_re\apktool_out <apk>
# → APKTOOL_EXIT=0
```

## 产物路径

| 路径 | 内容 |
|---|---|
| `.tmp_batteryguru_re/work/*.apk` | 原件只读 |
| `.tmp_batteryguru_re/jadx_out/` | Java 反编译 |
| `.tmp_batteryguru_re/apktool_out/` | Manifest / res / smali / lib |
