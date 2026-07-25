# 2026-07-25 · OneTools 电池追 AccuBattery（干净室 MVP）

## 目标

对齐 AccuBattery **核心差异点**（非抄源码）：充电会话测量 → 容量估算 → 健康度；可配充电闹钟；历史列表。

## 已做

| 能力 | 实现 |
|---|---|
| 实时仪表 | `BatteryReader` + 中文标签 |
| 插电跟踪 FGS | `BatteryChargeService` + `BatteryPowerReceiver` |
| 容量估算 | `ΔmAh / Δ% × 100`（单次 ≥20%） |
| 健康度 | 近 30 次有效样本均值 ÷ 设计容量 |
| 充电闹钟 | 默认 80%，可配 |
| UI | 实时 / 健康 / 历史 / 设置 |

## 未做（刻意）

- 分应用耗电 / 亮屏待机预估
- 闭源算法 / 机型库自动查设计容量（需用户手填）
- Widget

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
