# PrivilegeBridge 门面落地（OneBridge Phase 0）

**日期**：2026-07-15  
**类型**：架构解耦 / 过渡门面  
**版本策略**：本轮不改 versionName / versionCode（按「改 bug 不升版本」；本轮亦非发版）

## 动机

OneKuku 只要自家刚需，不要完整 Shizuku。先把客户端切到单一契约 `PrivilegeBridge`，底层暂用 `ShizukuPrivilegeBridge` 回落，为 Phase 1 自研桥替换铺路。

## 改动

- 新增 `com.oneims.app.core.privilege`：`PrivilegeBridge` / `ShizukuPrivilegeBridge` / `PrivilegeBridges`
- `OneKukuManager`、`ShizukuManager`、`SystemApiBroker` 改为经 `PrivilegeBridges.current`
- MVP 四服务闸门：`activity` · `carrier_config` · `isub` · `phone`
- 单测：`PrivilegeBridgeTest`
- 立项文档 Phase 0 门面项标为已落地

## 未做（刻意）

- 未实现 OneBridge 服务端
- MainActivity / GuardService 的 Shizuku listener 仍直连 SDK（Phase 2 再收口）
- 未改产品文案 / 未卸换皮 Core

## 验证

- `./gradlew :app:testDebugUnitTest --tests com.oneims.app.core.privilege.PrivilegeBridgeTest`
- `./gradlew :app:compileDebugKotlin`（或等价）
