# 变更说明：重启后 OneKuku 自动检查与恢复

**日期**：2026-07-14  
**范围**：开机编排 + 首页状态对接；不改 Hero 外壳布局；不恢复 APN；不切卡/飞行/radio。

## 流程

1. `BootReceiver`：BOOT_COMPLETED / USER_UNLOCKED / SIM_STATE_CHANGED → `OneKukuBootRestoreService`
2. 等待解锁 + SIM ready + 订阅列表稳定 → 再延迟 20s
3. 快照指纹匹配当前 SIM；配置仍有效则休眠且不通知
4. 失效则唤醒 OneKuku → `RESTORE_ALL_CALL_CONFIGS`（单项最多 2 次）
5. 无法唤醒/失败：首页红卡（NEEDS_ACTIVATION）+ 低优先级通知
6. 同开机仅自动恢复 1 次

## 验证

- `compileDebugKotlin` PASS
- 真机重启路径 NOT RUN
