# 变更说明 · 修复独家页信号格空架子

日期：2026-07-13  
范围：MainActivity / SystemDisplayOverrideManager / ConfigStore

## 根因

1. 独家页「应用信号格显示样式」误依赖能力页瞬态 `nr5g`；启动/切卡后 `nr5g=false`，固定 4/5 格会走「恢复基线」分支，看起来像空架子。
2. `setSignalStrengthAdjustmentEnabled(true)` 把任意开启态强制写成 `FIVE_BARS`，冲掉已选的 4 格偏好。

## 修复

1. 新增 `applySignalBarDisplay(mode)`：按精确模式写入/恢复，不看瞬态 NR。
2. 独家页 `onApplySignalBarStyle` 改走该入口。
3. 布尔偏好 setter / `applySignalStrengthAdjustment` 保留已选 `FOUR_BARS`。
4. 能力页「应用核心能力」传入 `preferenceMode`，避免冲模式。

## 说明

FOUR/FIVE 仍共用同一 CarrierIMS SSRSRP 阈值（不保证状态栏总柱数变为 4 或 5）；本次修复的是「点应用真的会写系统/恢复」，以及偏好不被冲掉。
