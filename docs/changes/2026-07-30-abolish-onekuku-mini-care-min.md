# 2026-07-30 · 清除 OneKuku 迷你版（CARE_MIN / `:care-min`）

## 决策

用户原话：「清除 onekuku 迷你版涉及的任何东西」。  
承接底包 3.0.4 + 废止本地 3.0.9：内循环只保留 **OneBridge**，不再保留 Care MINI / `onekuku_server` 融合线。

## 已清除（代码）

| 项 | 处理 |
|---|---|
| Gradle 模块 `:care-min` | 自 `settings.gradle.kts` 移除；目录 `care-min/` 删除 |
| `app` 依赖 `onekukuImplementation(project(":care-min"))` | 删除 |
| `ChannelEngine.CARE_MIN` | 枚举仅留 `ONEBRIDGE` |
| `ChannelBridgeBootstrap` | 固定 `OneBridgePrivilegeBridge()` |
| `OneKukuHostServerBootstrap`（onekuku） | 空操作桩（与 onelink 同） |
| `OneKukuCoreComponent` CareMin 分支 / `CARE_PACKAGE` 候选 | 删除 |
| onekuku `AndroidManifest`：`com.onekuku.care` queries、`ShizukuProvider` | 删除 |
| `ShizukuSetupHelper.CARE_PACKAGE` | 删除；Manager 列表仅官方包名 |

## 文档

下列架构/变更文保留作考古，文首应视为**已废止现行方案**（本文件为废止真源）：

- `docs/architecture/2026-07-30-onekuku-mini-*.md`
- `docs/architecture/2026-07-30-care-min-*.md`
- `docs/changes/2026-07-30-care-min-*.md`
- `docs/changes/2026-07-30-channel-engine-care-min-p0.md`
- `docs/changes/2026-07-30-why-still-onebridge-vs-care-min.md`

## 明确保留

- OneBridge（`:bridge` / `onebridge_server`）
- OneLink 外置 Shizuku
- `OneKukuMiniAdbClient`（无线调试激活客户端；**不是** Care MINI 产品线）

## 验证

```text
Test-Path care-min  → False
./gradlew :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin
./gradlew :app:testOnekukuDebugUnitTest --tests ChannelEngineTest --tests OneKukuCoreComponentTest
```
