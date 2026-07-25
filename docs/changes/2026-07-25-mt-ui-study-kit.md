# 2026-07-25 · MT UI 学习包（学习 / 模仿 / clean-room 重建）

## 背景

用户要「把源码搞出来」用于学习、模仿并重建 UI；在已有 jadx 业务树之上，再切一版 **UI 专用学习包**，并写明与 One Capsule 方案 B 的边界。

## 交付

本地路径：`.tmp_material_capsule_v155/mt_ui_study_kit/`

- `java/ui|graph|widget|…`（约 995 Java）
- `res/values` + `layout` + `drawable_sample`
- `LEARNING-ROADMAP.md` / `SCREEN-MAP.md` / `CLEAN-ROOM-REBUILD.md`

## 边界

学习包只读参考；禁止把 `com.pryshedko` / Billing / 改包逻辑嵌进业务仓。重建走 clean-room。
