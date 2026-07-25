# 2026-07-25 · 分应用耗电（对齐 AccuBattery 思路）

## 做法

干净室：放电采样电量计数/电量%跌幅 → 归因到 `UsageStats` 前台包名；区分亮屏/熄屏；通知显示约剩分钟。

## 依赖

- 使用情况访问（与 Meter 同源 `UsageAccess`）
- 跟踪开关开启时 FGS 持续采样（充/放都跟踪）

## 非目标

- 非 root `BatteryStats` 精确系统账本
- 闭源 AccuBattery 算法移植
