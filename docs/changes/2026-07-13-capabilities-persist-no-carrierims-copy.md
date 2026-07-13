# 变更说明 · 功能页去 CarrierIMS 文案 + 切卡开关持久化

## 问题
1. 功能页「5G信号强度调整」仍有整段「对齐 CarrierIMS」说明，产品不需要。
2. 切卡时 `LaunchedEffect(selectedSubId)` 强制把开关重置为运营商推荐 / 全关，用户已打开并应用的选择被冲掉。

## 方案
1. 重写 `signal_strength_adjust_*` / `signal_bar_needs_nr_enabled` 文案，去掉 CarrierIMS。
2. `ConfigStore.CapabilityUiState` 按 subId 持久化全部功能页开关；切卡只加载该卡快照；拨动/应用时写入。首次无快照才用运营商推荐基线。
