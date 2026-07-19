# 双产品 Root 开机开关

**日期**：2026-07-19  
**产品**：OneIMS（本仓）+ Shizuku（`E:\GQ\One\_forks\HSSkyBoy-Shizuku-clean`）

## 需求

两个产品各有 Root 开关：打开后开机分别拉起 OneIMS/OneKuku 与 Shizuku。

## 落地

### OneIMS（本仓）

- 新增 `ConfigStore.root_boot_start`（默认关）
- `RootBootStarter`：`su -c` 执行 `OneKukuCoreComponent.bridgeBootShellCommand`
- `BootReceiver` BOOT_COMPLETED 旁路调用（失败不影响原重放）
- 实验功能页：「Root 开机拉起通道」开关
- OneLink：本开关不冒充启动 Shizuku（文案引导去 Shizuku App）

### Shizuku（邻仓 clean）

- **原本已有** `KEEP_START_ON_BOOT` → `rootStart()`
- 强化：`rootStart` 在 libsu 失败时回退 `su -c Starter.internalCommand`
- 文案：开机启动（Root）说明更清楚；首页胶囊改为「开机启动」

## 验证

- OneIMS：`compileOnekukuDebugKotlin`（待跑）
- Shizuku：按邻仓既有 assemble 任务（待跑）
- 真机 Root 冷启：NOT RUN
