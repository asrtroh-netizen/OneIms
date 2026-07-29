# 2026-07-29 · 一加 IMS provisioning 软失败

## 日志

- `IMS provisioning rejected key=27, result=1`（WFC mode）
- `provision_vowifi_roaming` / `provision_wfc_mode` 未完整写入
- CarrierConfig 已生效；OneLink 已激活

## 根因

一加/高通 modem 常拒 AOSP key=26/27；旧逻辑把软失败当成整单 `success=false`，MainActivity 再抛成「操作失败」。

## 修复

- `ProvisioningWritePolicy`：26/27 对应 detail key 为软失败
- `ImsController.applyAll`：仅软键失败 → `treatAsSuccess=true` + OEM 随缘文案
- `setWfcMode`：OEM reject 不再当硬失败吓人

## 验证

- 单测 `ProvisioningWritePolicyTest`
- 双 flavor 编译
- 一加真机：NOT RUN
