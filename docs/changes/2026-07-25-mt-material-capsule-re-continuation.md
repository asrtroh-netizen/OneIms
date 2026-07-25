# 2026-07-25 · MT（Material Capsule）逆向续作

## 背景

接续本地工作副本 `.tmp_material_capsule_v155` 对 Material Capsule v15.5 PREMIUM 样本的逆向与 owner-free 改包。

## 本轮落地

- jadx 1.5.1 全量反编译 → `jadx_billing/`（BillingManager 可读）
- 坐实 Play productId：4×inapp + 2×subs
- apktool 重建 + zipalign + apksigner v3 → `mt_v155_owner_free_v23.apk`
- 文档：`CONTINUATION-MT-RE-2026-07-25.md`、更新 REPORT / FREE-BUILD-NOTES / field-journal / DELIVERY

## 未做

- adb 真机冒烟（本机无 adb）
- UnlockPro UI NOP
- 官方 Play 包差分

## 验证

```text
jadx --version  # 1.5.1
apktool b ...   # Built apk
apksigner verify --verbose mt_v155_owner_free_v23.apk  # v3 Verifies
```
