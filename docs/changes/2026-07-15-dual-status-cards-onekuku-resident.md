# 2.1.6 · 双状态卡 + OneKuku 常驻

## 动机

首页原先只有一张 `StatusHero`，把「通话逻辑」与「OneKuku 通道」揉在一起，且 READY 文案写成「休眠中」。产品要求：拆成两个状态块；OneKuku 单独一块且不休眠、常驻。

## 改动

1. **逻辑状态卡** `LogicStatusHero`：负责可恢复 / 等待通道 / 执行中 / 无 SIM；主按钮一键恢复（原底部紧急恢复区并入此卡）。
2. **OneKuku 通道卡** `StatusHero`：专责激活五态进度与检查状态；READY 改为「常驻」。
3. **常驻策略**：`OneKukuSleepController.sleepIfEnabled` 忽略 autoSleep，任务后一律 `markActive`；`refreshFromBridge` / `installBridge` 激活后标 ACTIVE。
4. 设置页去掉「用完自动休眠」开关，改为常驻说明。

## 版本

- `versionName=2.1.6` / `versionCode=51`

## 验证

- `:app:compileDebugKotlin`
- `:app:testDebugUnitTest --tests com.oneims.app.ui.OneKukuCardPolicyTest`
- 真机双卡布局与常驻文案：NOT RUN（需人工）
