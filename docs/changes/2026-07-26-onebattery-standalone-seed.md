# 变更说明 · OneBattery 独立模块种子（干净室重造）

## 背景

无 Battery Guru 源码；不做反编译。从本仓已删除的 OneTools 电池历史（AccuBattery 思路干净室）复活为独立 App。

## 交付

- 新模块 `:onebattery`，`applicationId=com.onebattery.app`，`0.1.0`
- 复活：充电会话 / 健康估算 / 分应用耗电 / Widget / Shizuku batterystats
- 包名全面改为 `com.onebattery.app`

## 验证

```text
.\gradlew :onebattery:assembleDebug
→ BUILD SUCCESSFUL
```

产物：`onebattery/build/outputs/apk/debug/onebattery-debug.apk`（另拷 `OneBattery-debug.apk`）

## Git

- `8535f7e` 种子提交曾误带 `onebattery/build/`
- `8024854` 已从版本库移除构建产物，并在 `.gitignore` 增加 `/onebattery/build/`

## 明确不做

- 反编译 / 克隆 `com.paget96.batteryguru`
- AOD 全功能、商标名 Battery Guru
