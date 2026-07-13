# 变更说明 · 信号格四/五格真正差分写回

## 背景
独家页「信号格显示样式」点了不生效：此前只修了路由/偏好，但 FOUR/FIVE 仍压缩成同一套 SSRSRP 单键 Bundle，SystemUI 总柱数不变。

## 方案
恢复 AOSP 显示层四键差分预设：

| 模式 | inflate | NR SSRSRP | LTE RSRP | parameters |
|---|---|---|---|---|
| 固定 4 格 | false | -110/-90/-80/-65 | -128/-118/-108/-98 | 1 |
| 固定 5 格 | true | -115/-105/-95/-85 | -125/-115/-105/-95 | 1 |
| 自动适配 | — | 恢复首次写入前基线 | — | — |

总柱数由 `inflate_signal_strength_bool` 控制（true → SystemUI levels +1）。

## 改动
- `SystemDisplayOverrideManager.kt`：`applySignalBarDisplay` / `applySignalStrengthPreset` 按模式写完整 Bundle；ownership 比较与持久化恢复四键。
- 文案 `signal_bar_style_notice`：去掉「不保证总柱数 / 必须 NR」误导。
- 单测：断言 FOUR≠FIVE（inflate + 阈值差分）。

## 验证
- `:app:testDebugUnitTest --tests SystemDisplayOverridePolicyTest`
- `:app:compileDebugKotlin` / `packageNamedDebugApk`
