# 2026-07-25 · VoNR 系统设置可见性：开 VoNR 时写 NR availabilities

## 现象

3.0.3 沙盒旁路 + 重启（不连 Shizuku）后，系统通话设置往往只见 VoLTE，不见 VoNR。

## 根因

AOSP Settings 露出 VoNR 需同时：

1. 设备具备 5G 能力  
2. `carrier_nr_availabilities_int_array` **非空**  
3. `vonr_setting_visibility_bool == true`  

此前 `applyAll(enableVonr)` 只写 vonr 布尔，NR 数组在单独的「5G NR」路径——用户只开 VoNR 时系统不露菜单。

## 修复

- `enableVonr=true` 时同包写入 `NR_AVAILABILITIES_NSA_AND_SA`（[1,2]）
- 增补 `vonr_on_by_default_bool`
- 覆盖发布仍用 tag `v3.0.3` / versionCode `73`
