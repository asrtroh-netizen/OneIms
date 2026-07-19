# Root 持久化增强 MVP（旁路叠加）

**日期**：2026-07-19  
**规模**：S～M

## 背景

有 Root 用户群体；要求在不动免 Root 主逻辑的前提下「加一点」Root 侧能力。

## 变更

- 新增 `RootPersistenceSupport`：通道探测、增强开关、上次 persistent 记账、可选结果文案后缀
- `ConfigStore`：`root_persist_enhance`（默认关）+ last override persist 记录
- `CarrierConfigOverrideWriter`：成功/清理结果经旁路装饰，**不改** try-persistent→temporary 主逻辑
- 实验功能页：Root 增强开关 + 状态行
- 一键体检：增加 Root/配置持久检查项

## 非目标

- 未改 BootReceiver / GuardService 重放语义
- 未做 Magisk/init 通道常驻（后续可选语义 B）
