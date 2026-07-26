# 借鉴 MT 反编译 → OneTools One Capsule（干净室）

## 材料
- 学习包：`.tmp_material_capsule_v155/mt_ui_study_kit/`（`STUDY-FIRST.md` / `capsuleui` / `animations`）
- 干净室参照：`E:\GQ\One\OneCapsule\_archive\...\CameraAwareCapsuleLayout.kt`、`CapsuleBoundsCalculator.kt`
- 硬边界：不拷 `com.pryshedko.*`、不搬 Billing / UnlockPro

## 学到并落地的心智
| MT / OneCapsule 心智 | OneTools 落地 |
|---|---|
| 挖孔为锚 / 展开下挂 | `CameraAnchorResolver` + `CameraAwareCapsuleLayout` |
| 避摄：下方 / 对齐摄像头 | prefs `cameraExclusionMode` + Live Lab 开关 |
| CAMERA_CENTER 中间留缝 | 双叶 pill（左主文案 \| 透明缝 \| 右 ETA） |
| 展开 ~300ms / 收起 ~260ms | `CapsuleMotion` + ViewPropertyAnimator |

## 版本
源码 `0.5.1`（本轮可不打包，看用户指令）
