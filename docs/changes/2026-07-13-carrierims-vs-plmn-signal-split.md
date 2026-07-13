# 2.0.5：阈值=CarrierIMS，格子=PLMN/inflate

## 口径（用户确认）
- **功能页阈值** → 对齐 CarrierIMS：只替换 `5g_nr_ssrsrp_thresholds_int_array=[-128,-118,-108,-98]`
- **独家页格子数** → PLMN/显示层：`inflate_signal_strength_bool`（+ LTE/parameters），不冒充 CarrierIMS 阈值

## 纠偏
2.0.4 组合写入曾误用五格 soft NR 阈值。现改为：
- `carrierImsNrSsrsrpThresholds()` 为唯一阈值真源
- `composeIndependentSignalPreset`：阈值开→写 CarrierIMS NR；格子 FOUR/FIVE→只改 inflate/LTE/parameters

## 验证
- `composeIndependent_*` 单测
- `packageNamedDebugApk` → `OneIms-2.0.5.apk`
