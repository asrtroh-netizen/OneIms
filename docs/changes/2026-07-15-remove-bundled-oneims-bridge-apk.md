# 2026-07-15 · 删除 assets 内嵌 oneims-bridge.apk（2.0.21）

## 原因
Phase4 后通道已 library 打进主包；`assets/oneims-bridge.apk`（约 5.1MB）为死资产，仍占 APK 体积。

## 变更
- 删除 `app/src/main/assets/oneims-bridge.apk`
- 更新 `ONEKUKU_CORE_README.txt`
- 版本 `2.0.21` / code `30`

## 验证
- `packageNamedDebugApk` 后 APK 应少约 5MB（相对 2.0.20）
