# 变更说明 · 5G 信号强度对齐 CarrierIMS

日期：2026-07-13  
范围：能力页 UI + `SystemDisplayOverrideManager` 信号路径

## 做了什么

1. **UI**：`CapabilitiesScreen` 中 VoLTE / VoWiFi / VoNR / 5G NR / 5G信号强度 五开关连排进「运营商能力与 5G」，中间不再插入应用按钮造成视觉分区。
2. **写入语义**：删除旧版 inflate / LTE RSRP / 多键大陆预设；对齐 CarrierIMS `ImsModifier`，仅写  
   `5g_nr_ssrsrp_thresholds_int_array = [-128, -118, -108, -98]`。
3. **NR 耦合**：系统写入条件为「信号开关 ∧ 5G NR」；用户偏好仍可单独保留。
4. **升级迁移**：同一次 override 回写旧多键 baseline（若存在），避免 AOSP `putAll` 残留。

## 验证

- `./gradlew :app:testDebugUnitTest --tests com.oneims.app.core.SystemDisplayOverridePolicyTest` → PASS
- 真机 CarrierConfig 回读：NOT RUN
