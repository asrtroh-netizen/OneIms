# Android 16 SDK + SIM 国家码覆盖

## 背景
- APK 曾命名 `android17`，但实际 `compileSdk/targetSdk` 为 35；官方 Android 17 = API 37。
- 本机 SDK 已有 platforms android-36，无 android-37；先对齐 Android 16（API 36）。
- 国家码覆盖原先仅 TikTok 一键写 `us`；现扩展为通用能力。

## 变更
1. **SDK**：`compileSdk`/`targetSdk` → 36；`versionName` 2.1.0 / `versionCode` 9；产物名 `OneIms-2.1.0-android16.apk`；`suppressUnsupportedCompileSdk=36,37`。
2. **`SimCountryIsoManager`**：统一 apply / clear / readCurrent / presets / TikTok US 预设；键 `sim_country_iso_override_string`。
3. **能力页 UI**：修复工具区增加 ISO 输入、常用预设芯片、应用/清除；保留 TikTok US 快捷入口。
4. **按卡草稿**：`ConfigStore.simCountryIsoDraft` 按 subId 持久化输入，避免切卡串值。

## 边界
- 只改上层读取的国家 ISO，不改基带 MCC/MNC。
- API 37 需安装 `platforms;android-37` 后再升 compile/target 与产物命名。
