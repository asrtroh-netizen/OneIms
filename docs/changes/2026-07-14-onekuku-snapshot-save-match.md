# 变更说明：配置快照保存与 SIM 匹配

**日期**：2026-07-14  
**范围**：`OneKukuSnapshotStore` / Factory / Restore / 四宫格摘要读取；不改 UI 壳；APN 不进自动/一键恢复。

## 做了什么

1. 快照条目字段对齐：`configGroup/key/value` + `writeMethod` + `persistent`；SIM 绑定含 `iccidHash`（仅短哈希）。
2. 存储升级 `snapshots_v2` 列表；匹配优先级：iccidHash → slot+carrierId+mccmnc → slot+mccmnc+carrierName → 旧 subId。
3. 无匹配返回「未找到与快照匹配的 SIM」，禁止写错卡。
4. 隐私：过滤 APN、密码/号码/激活码；UA 打码；日志 hash 打码。
5. 四宫格「配置快照」优先读 Store 摘要。

## 验证

- 本轮单元测试 / compileDebugKotlin
- 真机换卡匹配 NOT RUN
