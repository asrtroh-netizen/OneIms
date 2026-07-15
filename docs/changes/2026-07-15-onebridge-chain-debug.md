# 2026-07-15 · OneBridge 启动链路 Debug（不 push）

**版本策略**：不升 versionName / versionCode  
**约束**：本轮按用户要求 **不 push**

## 审计结论（根因）

| 级别 | 断点 | 处置 |
|---|---|---|
| P0 | `writeShell` 恒 true + `start_issued` 假成功 | 已修：解析 `OneBridge_started` / `OneBridge_missing`；Success 仅当 `isRunning()` |
| P0 | boot 使用 `exec app_process` 占死 ADB shell 流 | 已修：后台 `&` + echo 标记，shell 可返回 |
| P0 | 真机 binder 投递 | **仍 NOT RUN**（需真机 logcat） |
| P1 | `isAllowedUid` 放行任意应用 UID | 已修：按 `CLIENT_PACKAGE` 查 PackageManager |
| P1 | `CHECK_PERMISSION` 信任 parcel 自报 uid | 已修：只信 `getCallingUid()` |
| P1 | `ActivityThread.systemMain` 未初始化 | 已修：`BridgeService.main` 先 systemMain |

## 改动要点

- `OneKukuCoreComponent.bridgeBootShellCommand` / `bridge/assets/start.sh`：后台启动 + `OneBridge_started`
- `OneKukuEmbeddedAdbActivator`：shell 输出判定；轮询 binder ≤5s；失败码 `binder_not_received`
- `BridgeBinder`：包名白名单；忽略自报 uid
- `BridgeService`：systemMain + 投递失败显式日志

## 验证

- `:app:compileDebugKotlin` / `:bridge:compileDebugKotlin`
- `OneKukuCoreComponentTest` / `OneKukuEmbeddedAdbActivatorTest`
- 真机「启动通道 → binder received」→ **NOT RUN**
