# 变更说明 · 全链路同类小毛病排查与修复

## 检查结论（摘要）

| ID | 问题 | 处理 |
|---|---|---|
| A | CarrierIMS 用户文案 | 上轮已清；注释已同步 |
| B | 功能页切卡冲开关 | 上轮已修 |
| B-3 | 身份覆盖输入串卡 | **本轮修**：按 subId 草稿 |
| D-1 | 应用核心能力写死 FIVE 忽略 FOUR | **本轮修**：按 preferenceMode 写预设 |
| C-1/C-2 | Reapply/advanced 跨卡误写 | **本轮修**：subId 门禁 + 本卡快照 |
| C-3 | fiveGDisplayConfig 仍全局 | **记录**：下轮可改 per-subId |
| D-2 | 信号双入口 | **记录**：产品决策后再合并 |

## 验证
`:app:compileDebugKotlin` / `packageNamedDebugApk`
