# One Capsule 0.7.1 · 动态色 / 震动 / 手势可配 / 挖孔校准

对照 MT / OneCapsule 干净室（`feature/gestures`、`feature/calibration`、`hapticEnabled`、`dynamicColorEnabled`），落地到 OneTools Live Lab。

## 交付

| 能力 | 落点 |
|---|---|
| 壁纸动态色开关 | `CapsuleThemeColors` + prefs + Overlay 填色 |
| 震动反馈开关 | `CapsuleHaptics` · 展开/收起 confirm · 切会话 tick |
| 手势映射表 | `CapsuleGestureMap` · Live Lab 点按循环动作 · 可恢复默认 |
| 挖孔校准 | `cutoutCalibX/Y` 叠在 `CameraAnchorResolver` · 显示原始挖孔 · 重置 |

## 默认手势（与旧行为一致）

- 单击 → 展开/收起  
- 下滑 → 展开 · 上滑 → 收起  
- 左滑 → 下一会话 · 右滑 → 上一会话  

## 版本

`0.7.1` / `versionCode 30`
