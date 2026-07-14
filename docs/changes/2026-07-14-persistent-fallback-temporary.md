# 2.0.10：persistent=true 权限不足回退临时覆盖

日期：2026-07-14

## 根因

`CarrierConfigManager.overrideConfig(..., persistent=true)` 仅系统应用可调用。
OneKuku/Shizuku 非 system app → 全量 SecurityException，applyAll 0/13，clear 也挂。

## 修复

`CarrierConfigOverrideWriter.overrideConfigBestEffort`：先 true，命中
`only can be invoked by system app` 则回退 `persistent=false`；clear 同样。

## 影响

临时覆盖重启后可能丢失，仍依赖开机重应用；比完全写不进去好。
