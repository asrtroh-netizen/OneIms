# 2026-07-25 · SDK 沙盒持久旁路（南宫 3.1 Phase 2）

## 做什么

对齐 vvb2060/南宫 3.1 的 `INSTR_FLAG_INSTRUMENT_SDK_SANDBOX` + ContentProvider binder 握手路径，在平台探测仍允许持久时尝试真 `persistent=true`；失败回落既有 Writer（先 persistent 再 temporary）。

## 用户可见

- 实验页新增开关 **「沙盒持久旁路」**（默认关）
- 与 **「强制临时写入」** 互斥：临时优先，开临时则不走沙盒
- 诊断/策略标签成功时可为 `sandbox-instrumentation-persist`

## 代码入口

| 组件 | 职责 |
|---|---|
| `SandboxPersistSupport` | 门禁 + `startInstrumentation`（sandbox flags）+ 进程内 latch |
| `SandboxPersistInstrumentation` | 沙盒进程：handshake → 被委托后 `overrideConfig(persistent=true)` |
| `SandboxPersistProvider` | 主进程：校验 callingUid → `startDelegateShellPermissionIdentity(sdkUid)` → transact |
| `CarrierConfigOverrideWriter.overrideConfigBestEffort` | 强制临时 → 沙盒旁路 → 常规 persistent/temporary |
| Manifest | 注册 Instrumentation + `${applicationId}.sandboxpersist` Provider |

## 平台边界（诚实）

当探测为 `LIKELY_BLOCKED`（含 A17 `isSdkSandboxUidInternal`）时，本旁路**不会尝试**——与南宫 `canPersistent=false` 同门。  
「开了开关」≠「重启一定还在」。

## 验证

- `:app:compileOnekukuDebugKotlin` / `:app:compileOnelinkDebugKotlin` — PASS
- 单测 `SandboxPersistSupportTest` / `PersistentCapabilityProbeTest` — 见本轮 CI/本地输出
- 真机沙盒路径：需探测 ALLOWED + 特权通道就绪；A17 BLOCKED 设备仅验证「不误报成功」
