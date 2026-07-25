# 2026-07-25 · OneTools 接入网速监测 + 电池页

## 做了什么

- **Meter**：`PhysicalSpeedSampler` + `SpeedMonitorService`（通知栏实时上下行；忽略 VPN）
  - 改编自 Pixel Meter `SpeedDataSource`（Apache-2.0），见 `onetools/NOTICE`
- **Battery**：`BatteryReader` 干净室只读公共 API（字段集合参考 Battery Info，不拷源码）
- 首页卡片入口 → `BatteryScreen` / `MeterScreen`

## 验证

```powershell
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```

BUILD SUCCESSFUL；`SpeedFormatTest` / `BatteryReaderTest` / `ChannelCardPolicyTest` PASS。

## 未做

- Pixel Meter 完整悬浮窗 / QS Tile / Koin 架构
- Battery Info 设计容量 PowerProfile 私有 API / Widget
- 真机手测 **NOT RUN**
