# 变更说明 · 信号格样式系统全局应用封装

## 目标
保留独家页「信号格显示样式」现有 UI，只把「应用」改为对 `selectedSubId` 的系统全局 CarrierConfig 尝试。

## 方案
新增 `SignalBarSystemStyleManager`：
- `applyAuto` / `applyFourBars` / `applyFiveBars` / `clearOverride`
- `apply(mode)`：校验 subId → 可读 CarrierConfig → 写四键或清基线 → 回读验证
- `readCurrentStyle` / `verifyStyle`

`MainActivity.onApplySignalBarStyle` 改为调用该 Manager；底层仍复用 `SystemDisplayOverrideManager` ownership，不误伤能力页信号强度路径。

## 文案
成功固定 4/5 格：提示「已写入可用配置，但总柱数取决于 SystemUI/ROM」。
无可写配置：提示「当前系统暂不支持…」。
失败：具体原因，不假成功。
