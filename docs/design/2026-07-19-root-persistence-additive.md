# Root 场景持久化 · 可叠加方案（不动既有免 Root 主路径）

**日期**：2026-07-19  
**状态**：MVP 已落地（旁路可观测 + 开关）· 通道级 Magisk 常驻仍待选型  
**规模**：S～M  
**约束**：不改动、不破坏当前免 Root / Shizuku / Boot 重放主逻辑；Root 能力只作旁路叠加

### MVP（2026-07-19）

| 项 | 落点 |
|---|---|
| 旁路模块 | `RootPersistenceSupport` |
| 偏好 | `ConfigStore.isRootPersistEnhance`（默认关）+ 上次 persistent 记录 |
| 写入挂钩 | `CarrierConfigOverrideWriter` 仅装饰结果文案/记账，不改 try-persistent 主逻辑 |
| UI | 实验功能页「Root 增强」开关 + 状态行 |
| 诊断 | `OneClickDiagnosticsManager` 增加 Root/持久检查项 |

---

## 1. 现状事实（代码证据）

| 事实 | 证据 |
|---|---|
| CarrierConfig **已优先** `persistent=true`，被拒则回退 temporary | `CarrierConfigOverrideWriter.overrideConfigBestEffort` |
| Root 通道 = 特权进程 `uid==0`，走直调而非 Instrumentation | `SystemApiBroker.overrideConfig` → `shizukuOverride`；`OneKukuManager.isRootChannel()` |
| 守护注释写明：非 system app 无法真正 persistent，靠开机重放 | `GuardService` 类注释；`BootReceiver` → `OneKukuBootRestoreService` / `ReapplyManager` |
| 产品默认叙事为「免 root」 | `strings.xml` `about_version` |
| 设计文档已点明：非 Root 重启后通常需再拉通道 | `docs/design/2026-07-15-onebridge-privilege-min.md` §0.1 |

结论：**不是「完全没有持久化」**，而是「配置层已 try-persistent + 失败临时 + Boot/Guard 重放」；Root 直调已存在，但 **不等于** 系统级 `persistent=true` 一定成功，也不等于「通道开机永久守护」。

---

## 2. 「持久化」语义需二选一（或分阶段）

| 语义 | 用户体感 | 现有覆盖度 | 叠加成本 |
|---|---|---|---|
| **A. 配置持久化** | 重启后 CarrierConfig 覆盖仍在，少依赖重放 | 已 try；Root 可能仍被拒（非 system app） | 低～中：探测真实 `Result.persistent` + Root 专用提示；真要系统级需 system 化/Magisk |
| **B. 通道持久化** | Root 下开机自动起 OneKuku/桥，少点「启动通道」 | 现有 Boot 偏无线调试/Shizuku，非 Magisk 常驻 | 中：新增 RootBoot 旁路（init/Magisk service / `su -c` 拉起），与现 BootReceiver 并行 |
| **C. 两者都要** | A+B | 部分 | M～L，分阶段 |

「不动原有逻辑」下 **推荐先做语义 B 的旁路探测+开关，或语义 A 的可观测性增强**；避免改写 `CarrierConfigOverrideWriter` 主路径。

---

## 3. 方案矩阵（推荐叠加，禁止侵入）

### 方案 0 · 不做（基线）

- 继续：temporary + Boot/Guard 重放  
- 风险最低；Root 用户仍可能每次重启依赖通道就绪后的 reapply

### 方案 1 · 旁路「Root 持久化门面」（推荐优先）

新增独立模块（示意名 `RootPersistenceGate`），**只读探测 + 可选动作**，不改现有写入/Boot 主路径：

1. `OneKukuManager.isRootChannel()` 为真时启用  
2. 写入后读 `CarrierConfigOverrideWriter.Result.persistent` 上报真实模式（文案与诊断）  
3. 可选：Root 下额外 `su`/init 钩子拉起通道（语义 B），失败则静默回落现有 Boot 编排  
4. 功能开关默认关，免 Root 路径零感知

**侵入面**：新增文件 + Settings/诊断入口；现有 `overrideConfigBestEffort` / `BootReceiver` / `GuardService` **不改语义**。

### 方案 2 · Magisk 模块 / system 化（真 · 配置持久）

- 将写 CarrierConfig 的身份提升到 system（或 Magisk 挂载 system 组件）  
- 才能稳定拿到 `overrideConfig(..., persistent=true)` 的系统级许可  
- 与「免 Root 主产品」分轨发布；维护与签名成本高

**侵入面**：制品链路、安装说明、安全边界；业务 Kotlin 可仍走同一 Writer。

### 方案 3 · 改写主路径强制 persistent（不推荐）

- 在 Writer/Broker 里按 Root 分支改默认策略或去掉 temporary 回退  
- **违反「不动原有逻辑」**；免 Root 回归风险高 → **否决**

---

## 4. 推荐落地顺序

1. **确认语义**（A / B / C）  
2. 方案 1：开关 + 诊断「实际 persistent？」+（若选 B）Root 开机拉起旁路  
3. 真机矩阵：Root 有/无 × 重启 × 杀通道 × SIM 未就绪  
4. 仅当 A 在 Root 下仍长期 temporary，再评估方案 2

---

## 5. 验收草案

| 场景 | 期望 |
|---|---|
| 免 Root | 行为与今日一致（开关关时） |
| Root + 开关关 | 与今日一致 |
| Root + 开关开 + 语义 A | 诊断显示真实 persistent；若系统允许则重启后无需重放即可保留覆盖 |
| Root + 开关开 + 语义 B | 冷启后通道自动就绪（或明确失败原因），现有 Boot 重放仍可作兜底 |
| 应急清空 / SafetyGuard | 仍可清 override，不留「只写不还」 |

---

## 6. 明确非目标

- 不把产品默认改成「必须 Root」  
- 不删 Boot/Guard 重放（Root 失败时仍是兜底）  
- 不做完整 Magisk 管理器 / 通用 su 框架
