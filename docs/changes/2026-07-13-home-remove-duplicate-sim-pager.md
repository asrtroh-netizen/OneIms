# 变更说明 · 首页去掉重复 SIM 胶囊

日期：2026-07-13  
范围：首页 UI（`HomeScreen`）

## 做了什么

1. **UI**：从「快速开始」区块移除 `SimStatusCapsulePager`（卡 N / 使用中 + 圆点）。
2. **原因**：与上方 `StatusHero` 内同款 SIM 状态胶囊重复，造成信息冗余；顶栏 `SelectedSimPill` 仍负责切卡。

## 验证

- 代码对照截图定位 → PASS（仅 `HomeScreen` 快速开始内一处重复）
- 真机目视确认布局间距 → NOT RUN（需本机安装后看首页）
