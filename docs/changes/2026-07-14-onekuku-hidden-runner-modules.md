# 变更说明：OneKuku 后台执行模块

**日期**：2026-07-14  
**范围**：新增 `onekuku` 包内部模块 + 首页恢复/快照接线；不重做 UI 布局。

## 做了什么

1. 新增白名单执行链路：
   - `OneKukuHiddenRunner`：唤醒 / 执行中 / 休眠 / 失败状态机
   - `OneKukuCommandDispatcher`：仅白名单命令，无通用 shell
   - `OneKukuRestoreManager`：按快照顺序恢复（单项失败不阻断）
   - `OneKukuSnapshotStore`：Prefs JSON 快照；ICCID 仅存短哈希
   - `OneKukuSleepController`：任务结束后休眠
2. `OneKukuPrivilegeBridgeImpl` 注入既有 `OneKukuManager` 就绪态（onekuku 包不直接引用第三方特权 SDK）。
3. 首页「一键恢复通话」改为 `RESTORE_ALL_CALL_CONFIGS`；应用核心/推荐配置成功后写入快照；旧 ConfigStore 快照可迁移。

## 刻意未做

- 删除 rikka / SystemApiBroker 实现
- 独立 OneKuku 守护进程（当前特权仍经既有门面）

## 验证

- `compileDebugKotlin` / `OneKukuCommandDispatcherTest`（本轮执行）
- 真机恢复全路径 NOT RUN
