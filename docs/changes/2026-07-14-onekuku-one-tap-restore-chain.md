# 变更说明：一键恢复通话完整执行链

**日期**：2026-07-14  
**范围**：`OneKukuCallRestoreExecutor` + MainActivity 接线 + RestoreManager 分项失败记录；不改 UI 外壳。

## 流程

执行中 → SIM/快照匹配 → 唤醒/激活 OneKuku → 身份→IMS→WFC→5G→信号→VoWiFi名 → 汇总三态 → 休眠 → 写恢复记录 → 更新状态卡。

## 验证

- compileDebugKotlin PASS
- 真机点击路径 NOT RUN
