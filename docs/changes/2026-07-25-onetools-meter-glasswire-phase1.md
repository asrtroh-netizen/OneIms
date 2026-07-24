# 2026-07-25 · Meter 对标 GlassWire Phase1（应用流量）

## 背景

Meter 可比清单第 2 项：GlassWire（闭源）。本轮 **clean-room** 补齐「按应用累计流量」，不复制其代码/商标；VPN 防火墙刻意延后，避免与 One 代理栈双 VPN 互抢。

## 落地

- `AppTrafficReader` + `TrafficWindow`：`NetworkStatsManager` 汇总 Wi‑Fi / 移动 / 全部
- `UsageAccess`：`PACKAGE_USAGE_STATS` AppOps 检测与设置跳转
- `TrafficVolumeFormat`：累计字节格式化
- `MeterScreen`：今日 / 近 7 日 / 近 30 日 + 网络筛选 + Top40 列表
- Manifest 声明 `PACKAGE_USAGE_STATS`；`NOTICE` 注明 GlassWire 仅为能力对标

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
