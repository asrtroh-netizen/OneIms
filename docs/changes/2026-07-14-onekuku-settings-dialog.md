# OneKuku 设置弹窗

日期：2026-07-14

## 变更摘要

首页四宫格「OneKuku 设置」弹窗补齐三项开关与状态文案，不新增独立设置页。

## 用户可见行为

1. **状态**：未激活 / 休眠中 / 执行中 / 失效
2. **开机自动检查**（默认开）：重启后检查通话配置是否仍有效
3. **自动恢复**（默认开，新增）：配置失效时才尝试唤醒并恢复；关闭则只提示需激活
4. **用完自动休眠**（默认开）：恢复结束后按开关决定是否休眠
5. **重新激活 OneKuku** / **检查 OneKuku 状态**（只读，不写不恢复）

## 实现要点

- `ConfigStore.KEY_ONEKUKU_AUTO_RESTORE` + get/set
- Boot 协调器：失效且关闭自动恢复 → `NEEDS_ACTIVATION`，不自动唤醒
- `OneKukuSleepController.sleepIfEnabled(context)` 贯穿恢复链路
- `HomeUiState.autoRestore` / `HomeActions.onAutoRestoreChange` 接线到 `MainActivity`

## 非目标

- 不引入 Shizuku / 终端文案到用户面
- 不改其他 Tab 页面布局
