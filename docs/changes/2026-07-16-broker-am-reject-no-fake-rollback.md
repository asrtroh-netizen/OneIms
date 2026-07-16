# BrokerInstrumentation 被拒时勿误报「回滚失败」

日期：2026-07-16

## 现象

IMS 能力页一键配置后提示「配置写入与自动回滚均失败」；排障日志含：

- `ActivityManager rejected BrokerInstrumentation`
- `batch 0/13` / `partial/fail`
- `clear failed`

## 根因（应用层）

1. 非 root 写入依赖 `IActivityManager.startInstrumentation`；返回 `false` 时在 `SystemApiBroker` 映射为上述文案，且 `operationStarted=false`。
2. `CarrierConfigOverrideWriter` 吞掉该异常并聚合成 `partial/fail`，`ImsController` 误走 `SafetyGuard.restoreDefaults`；回滚与写入共用同一 Broker，必然同败，放大恐吓文案。

AMS 返回 `false` 的深层 OS/OEM 原因仍需真机 logcat 钉死（身份 / 组件解析 / 残留 instrumentation）。

## 本轮改动

- Writer：未开写的 `BrokerExecutionException` 原样上抛，跳过假回滚。
- `OperationFeedbackPolicy`：将 AM rejected 归入权限/委托失败反馈。
- `SystemApiBroker`：拒绝时附带 channel / bridgeUid / appUid。
- OneBridge 客户端：远程 transact 异常打日志并上抛，禁止裸 `catch → false`。

## 验证

- 单测：`CarrierConfigOverrideWriterTest`、`CorePoliciesTest` 相关用例（含截图同款「AM reject + 自动回滚也失败」归类、`channel=onelink`）。
- 真机（**必须重装含本修复的 onelink APK**）：
  1. OneLink 已授权（官方 Shizuku 运行中）→ IMS 能力 → 应用核心能力。
  2. 若 AMS 仍拒：Snackbar 应为「权限代理启动失败 / 配置未写入」（OneLink 文案引导重启 Shizuku），**不得**再出现「写入与自动回滚均失败」。
  3. 排障日志应含 `channel=onelink`、`bridgeUid`、`appUid`。
  4. 若仍 `rejected`：在 Shizuku 内 Stop→Start 后重授权再试；仍败则抓 `adb logcat` 中 AMS/`startInstrumentation` 拒绝原因（属 OS/OEM 层，非文案问题）。
