# 2026-07-25 · Meter 对标 OEM 状态栏网速

## 背景

用户纠正：Meter 可比第 2 项是 **OEM 厂商状态栏网速**，不是 GlassWire。

## 落地

- `MeterChipFormat`：≤7 字符芯片文案
- `SpeedMonitorService`：API 36+ 平台 `setRequestPromotedOngoing` + `setShortCriticalText`；低版本通知 + 贴顶悬浮
- 设置项「状态栏芯片（Android 16+）」
- 「悬浮窗贴到 OEM 位」→ `ACTION_DOCK_OEM` 右上贴顶
- 保持 `androidx.core:core-ktx:1.13.1`（避免强升 AGP）

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
