# 2026-07-30 · 新开本地仓 ShizukuDropIn-Local（Drop-In + V15 OEM）

## 决策

| 机型 | 策略 |
|---|---|
| Pixel | **不动**：继续现有 `HSSkyBoy-Shizuku-clean` V15 |
| 小米等 OEM | 新仓 Drop-In（官方包名）+ 本地冷启特性 |
| `thejaustin/ShizukuPlus` 远端 | **只读参照**，不 push、不改他们的仓库 |

## 新仓路径

`E:\GQ\One\_forks\ShizukuDropIn-Local`

- 底座：ShizukuPlus `v13.6.0.r2185`
- 默认 flavor：`dropin` → `moe.shizuku.privileged.api`
- 版本标签：`Shizuku DropIn-Local 13.6.0.r2185+oem1`
- 说明：`README.LOCAL.md`

## oem1 已落地

- `BootCompleteReceiver` Direct Boot 分流 + Wi‑Fi/解锁武装
- `UserPresentRestartReceiver`（0/5/15s）
- `WifiReadyMonitor`
- `ShizukuReceiverStarter.rootStartViaSuC` 兜底
- Manifest 注册 `UserPresentRestartReceiver`（默认 disabled，boot 时启用）

## 验证

见交付总结；全量 `assembleDropinRelease` 依赖本机 SDK/签名，可能 NOT RUN。
