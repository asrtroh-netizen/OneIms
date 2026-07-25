# 变更说明 · OneTools 移除电池模块

## 背景

电池能力将另做独立 App；OneTools 暂不考虑维护。

## 删除范围

- `com.onetools.app.battery` 源码与 AIDL、单元测试
- UI：`BatteryScreen` / `BatterySparkline` / 未接线的 `CallerMeterHubScreen`
- 底栏 `ToolsDestination.BATTERY`、首页「电池跟踪」开关
- Manifest：`BatteryChargeService` / `BatteryPowerReceiver` / `BatteryWidgetProvider`
- Widget 布局与相关 strings

## 验证

```text
.\gradlew :onetools:compileDebugKotlin
→ BUILD SUCCESSFUL
```
