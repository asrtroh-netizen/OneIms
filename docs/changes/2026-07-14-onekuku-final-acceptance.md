# OneKuku 最终验收收尾

日期：2026-07-14

## 结论摘要

首页三块结构与恢复链路主体达标。本轮仅做最小合规修补，不新增功能、不拆底层依赖。

## 验收对照（修补后）

| 项 | 结果 |
|---|---|
| 结构（总控 / 四宫格 / 底部恢复） | PASS |
| 未激活红 / 已激活白 | PASS（就绪改 surface 白底） |
| 一键恢复唯一入口 | PASS（总控休眠态改为检查状态） |
| 恢复链路 / 开机 / 防错卡 / 无快照 | PASS |
| 回读验证 | 加强为 `isSnapshotEffective` |
| Shizuku 用户文案（首页） | PASS（无字面 Shizuku） |
| Shizuku 底层 SDK | 残留报告，本轮不拆 |

## 本轮修补

- `StatusHero` 就绪色 → `surface` + 轻阴影
- 总控卡 SLEEPING/COMPLETE →「检查状态」；底部唯一「一键恢复通话」（Filled Button）
- `RestoreManager.verify` → 复用开机侧快照有效性回读

## 仍保留（按优先级：先报告不大重构）

- `rikka.shizuku` 依赖与 `OneKukuManager` 底层桥
- 排障页 Termux / 准备入口（非首页）
- 变量名 `shizukuRunning` 等内部标识
