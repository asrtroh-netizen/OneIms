# 2026-07-25 · 系统持久探测 + 强制临时写入开关

## 范围

- 一键体检接入 `CarrierConfigLoader.isSystemApp` 族探测（对齐 vvb2060 `canPersistent` 决策，只读）
- 实验功能页新增「强制临时写入」开关（默认关）
- Writer：开关开启时跳过 `persistent=true`，直接 temporary

## 落点

| 能力 | 文件 |
|---|---|
| 探测 | `PersistentCapabilityProbe.kt` + 单测 |
| 诊断项 | `OneClickDiagnosticsManager` → `platform_persistent` |
| 开关偏好 | `ConfigStore.isForceTemporaryOverride` |
| UI | `ExperimentalScreen` / `ExperimentalUiState` / `MainActivity` |
| 写入 | `CarrierConfigOverrideWriter.overrideConfigBestEffort` |
| 文案 | `values` / `values-en` |

## 验收

- 开关关：行为与今日一致（先 try-persistent 再回退）
- 开关开：日志可见 skip persistent；结果 `persistent=false`
- 体检可见「系统持久能力探测」与 isSystemApp/sandbox 信号
- 单测：`PersistentCapabilityProbeTest`、既有 `CarrierConfigOverrideWriterTest`
