# 2026-07-30 · 国产 VoWIFI OEM 统一门控

## 需求

vivo / OPPO / 一加 / 小米同一套处理：不走 Pixel 通信主战场，主要保 VoWIFI。

## 改动

- `OemDeviceCompat.isDomesticVowifiOem()`：覆盖小米系 + vivo/iQOO + OPPO/realme/OnePlus + **三星** + 荣耀/华为/魅族等；**排除 Google/Pixel**
  - 三星/荣耀：通信本身正常，门控只服务 VoWIFI 容错
- 回读 soft-timeout、provisioning soft 白名单改挂该门控
- 国产额外 soft：key **28**（VoWIFI）+ key **10**（VoLTE 拒写不挡 VoWIFI 成功）
- Pixel：key **10 仍硬**

## 验证

- `OemDeviceCompatTest` / `ProvisioningWritePolicyTest`
- 双 flavor 编译
