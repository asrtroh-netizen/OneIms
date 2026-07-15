# 2.0.35 · 开机恢复：缩短等待 + 静默激活 + 检查默认开

## 采证（清数据后机上状态）

- `oneims_prefs` 几乎空：无 `guard_enabled` / `lastApplied` / 能力快照
- 无 `onekuku_snapshots` → **快照不齐全**（须先成功应用一次通话配置）
- `onekuku_auto_restore` 默认 true，但旧逻辑开机检查绑守护且默认关 → 整条不跑

## 改动

1. `POST_READY_DELAY_MS`：20s → **5s**（SIM 稳定等待仍保留）
2. `ensureOneKukuReadyForBoot`：wake 失败后走 `activateExistingOrNeedPair`；要码则挂配对通知
3. `isOneKukuBootAutoCheck`：独立键，**新装默认开**；写入仍同步 `guard_enabled`

## 版本

- 2.0.35 / versionCode 44
