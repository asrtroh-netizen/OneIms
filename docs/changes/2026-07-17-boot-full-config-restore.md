# 开机全量配置恢复（高级选项等）

日期：2026-07-17

## 问题

冷开机后未完整应用开机前已成功写入的配置；用户点名「应用高级选项」易丢失。根因是双轨恢复契约不一致：高级选项只挂在苛刻的 `ReapplyManager`（依赖 `lastApplied`/subId），且不进快照 `RESTORE_ALL`。

## 方案

1. **`ReapplyManager`**：核心与高级解耦；按卡重放 extras / nr5g / 信号 / 信号格；5G 显示按归属卡重放。
2. **归属键**：`advanced_sub_id`、`five_g_display_last_sub_id`，禁止双卡串写。
3. **开机协调器**：有任意持久重放源即 reapply；SIM 未稳不 `markAttempted`；收紧/拆分 `isSnapshotEffective` 与 `isSnapshotCarrierVerified`。
4. **快照 + RESTORE_ALL**：按卡写入 advanced / extras / five_g_display，并在恢复管线执行。

## 刻意不恢复

- APN（产品排除）
- 专家单键（无持久重放源）
- SIM 国家码草稿（草稿 ≠ 已应用）

## 验证

- `compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin`
- 真机冷开机双卡场景：人工清单（见交付总结）
