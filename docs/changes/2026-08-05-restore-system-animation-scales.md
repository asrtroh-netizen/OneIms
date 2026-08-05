# 恢复系统动画倍率（开发者选项）

## 现象

真机 UI 动画「看不见 / 像关掉」；上一轮只处理了挂死 preload / force-stop，未核对系统开关。

## 根因

Pixel 9 Pro Fold（`comet` / `CP2A.260705.006`）三项 global 均为 **0**：

| 设置键 | 修复前 | 修复后 |
|---|---|---|
| `window_animation_scale` | 0 | 1 |
| `transition_animation_scale` | 0 | 1 |
| `animator_duration_scale` | 0 | 1 |

仓库内 App / oneso / temp-root 脚本**未写入**这些键；多半是开发者选项或其它工具关掉的。

## 处置

```text
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
```

本轮用 `E:\GQ\One\_toolchain\android-sdk\platform-tools\adb.exe` 现场写回并二次读取验收。

## 非目标

- 不改 App 动画代码、不追查卡顿
- 不重装包、不改 temp-root PC 脚本逻辑
