# 变更说明 · OneBattery P0：功率 W · 多闹钟 · 常驻通知

**日期**：2026-07-26  
**规模**：M  
**参照**：Battery Guru 社区包 2.5.0.5（主）/ derrin 2.5.0.3（旁证，仅结构）

## 1. BatteryInfoService 读电链路（交叉对照）

| 环节 | 2.5.0.5（主参照） | derrin 2.5.0.3（旁证） | OneBattery 干净室落点 |
|---|---|---|---|
| 进程边界 | `services.BatteryInfoService` 独立进程 `:battery_service` | 同组件名/同进程声明 | 仍在主进程 FGS（P0 不拆进程） |
| 采样周期 | 服务内协程循环（R8 后难读定值；UI/通知侧见 `defpackage`） | 同骨架 | `BatteryChargeService` **15s** tick |
| 电量%/温度/电压 | `ACTION_BATTERY_CHANGED` extras | 同 | `BatteryReader` → `EXTRA_LEVEL/TEMPERATURE/VOLTAGE` |
| 电流 | `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`（公开 API 路径） | 同 | 同上 → `currentNowMa` |
| 功率 W | UI `watts_formatted`；`h31`/`zd5` 用电压×电流格式化 | 同字符串资源 | `powerWatts = V×I`（mV/1000 × mA/1000） |
| 闹钟通道 | `g25`：charging_limit / temperature_protection / high_battery_drain / full_charging… | 同 | 充满 + 低电 + 高/低温（无异常耗电，留给 P1） |
| 常驻通知 | FGS + 可配 icon/priority | 同 | 可开关 `persistentNotifEnabled`，文案 `% · °C · W · mA` |

**纪律**：不复制 Guru 源码/改包解锁；字段语义对齐公开 API。

## 2. 本轮代码交付

| 能力 | 实现 |
|---|---|
| 功率 W | `BatterySnapshot.powerWatts`；实时 Tab + Widget meta |
| 多闹钟 | 充满 / 低电 / 高温 / 低温；独立通知 ID；条件恢复后重新武装 |
| 常驻通知 | Pref + FGS 文案；跟踪关闭时仍可仅开通知 |

## 3. 验证

```text
.\gradlew :onebattery:assembleDebug
```

人工：设置页开关常驻通知与各闹钟 → 看通知栏刷新；插电看 W。
