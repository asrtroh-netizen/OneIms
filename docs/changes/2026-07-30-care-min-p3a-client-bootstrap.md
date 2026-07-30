> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · CARE_MIN P3a：客户端与拉起命令（默认仍 ONEBRIDGE）

**状态**：已落地（编译 + 指定单测绿）  
**范围**：onekuku / 共享 `main`；不改默认 `BuildConfig.CHANNEL_ENGINE`；不删 `:bridge`。

## 变更要点

1. `onekukuImplementation` 增加 `dev.rikka.shizuku:api/provider:13.1.5`（与 onelink 同版本）。
2. `ShizukuPrivilegeBridge` 抽到 `app/src/main/...`；onelink `ChannelBridgeBootstrap` 仍直接使用。
3. onekuku Manifest 增加 `${applicationId}.shizuku` 的 `ShizukuProvider`（与 `.onebridge` 共存）。
4. onekuku `ChannelBridgeBootstrap`：`CARE_MIN` → `ShizukuPrivilegeBridge()`（不再回落 OneBridge）。
5. `OneKukuCoreComponent.bridgeBootShellCommand` 按 `ChannelEngine` 选 nice-name / 入口类：
   - ONEBRIDGE：`onebridge_server` + `com.oneims.bridge.server.BridgeService`
   - CARE_MIN：`onekuku_server` + `rikka.shizuku.server.ShizukuService`（类进 APK 依赖后续 server 迁入）

## 验证

```bash
./gradlew :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin \
  :app:testOnekukuDebugUnitTest --tests ChannelEngineTest --tests OneKukuCoreComponentTest
```

结果：`BUILD SUCCESSFUL`。

