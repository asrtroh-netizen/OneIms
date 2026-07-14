# 变更说明：OneKuku CarrierConfig 写入规则统一

**日期**：2026-07-14  
**范围**：业务层 CarrierConfig 写入；**不改 UI**；不动 APN / QS 切卡 / LPA。

## 做了什么

1. 新增 `CarrierConfigOverrideWriter`：`applyPersistentOverride` / `clearPersistentOverride` / `readConfigForSubId` / `verifyOverride`；写前记录「当前目标：卡N · 运营商 · subId=…」。
2. `SystemApiBroker` + `BrokerInstrumentation` 放开并传递 `persistent`（默认业务路径 `true`）。
3. IMS / 5G NR / 身份 / 信号 / VoWiFi 名 / Pixel 高级 / SIM 国家码 / SafetyGuard 清空，一律改走 Writer；成功后自动落 OneKuku 快照。
4. 单项 key 失败记入 detail，不假成功。

## 刻意未做

- 改首页/能力页 UI
- 改 APN 页、QS Tile 切卡、LPA/eSIM、5G 显示增强 UI 布局

## 验证

- `compileDebugKotlin` / `CarrierConfigOverrideWriterTest`（本轮）
- 真机双卡写 selectedSubId + 重启持久化 NOT RUN
