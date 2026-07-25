# 2026-07-25 · 电池工程三缺口补齐

## 落地

| 缺口 | 实现 |
|---|---|
| Pixel 设计容量预设 | `PixelDesignCapacity` + 设置芯片 / 检测本机；未手改时启动 `applyPixelDesignIfUnset` |
| 桌面 Widget | `BatteryWidgetProvider` + 节流 `BatteryWidgetTick`；点击进电池页 |
| Shizuku UserService dumpsys | `IBatteryShell` / `ShellBatteryService`；`BatteryStatsShizuku` 优先 UserService，回退 `newProcess` |

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
