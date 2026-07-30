# 2026-07-30 · 产品定调：OneIMS = Lite⊕Shizuku 的内循环增强版旧 OneKuku

**状态**：冻结（用户原话：「OneIMS lite+shizuku 怎么合作的，OneIMS 这个 APP 里就怎么运转」「增强了以前的旧 OneKuku」「内循环」）  
**同构补充（2026-07-30）**：用户原话——「之前的东西逻辑一律不变；独立版本和组合版本没一点差异，差异就是独立和分割。」

## 0. 发行形态不变量（最高层）

| 形态 | 工程线 | 含义 |
|---|---|---|
| **分割（组合版）** | `onelink` = Lite + 外置 Shizuku | 通道 App 与业务 App **拆开** |
| **独立（一体版）** | `onekuku` = OneIMS 内循环 | 通道 + 业务 **合在一个 App** |

**逻辑同构**：协作步骤、业务门面（`PrivilegeBridge` → `SystemApiBroker`）、用户可见能力应对齐；  
**允许的唯一差异**：进程/包是否拆成第二个 APK（及由此带来的激活入口文案）。  
禁止借「独立/分割」演化出两套不同业务语义或两套互不相干的状态机。

## 一句话

| 产品 | 发行形态 | 通道侧（保活/拉起） | 业务侧（首页/写配置） |
|---|---|---|---|
| **OneIMS Lite / OneLink** | **分割** | 外置官方 Shizuku | Lite App |
| **OneIMS（onekuku）** | **独立** | **增强旧 OneKuku**（内嵌桥 / 目标内置 MINI server + 无线配对/冷启） | **同一个 App** |

角色分工相同；差别只是通道侧是否拆成第二个 APK（独立 vs 分割）。

## 协作步骤镜像

| 步 | Lite + Shizuku | OneIMS 内循环（增强 OneKuku） |
|---|---|---|
| 1 通道进程起来 | 用户在 Shizuku 里 Start | 首页「立即激活」→ 无线调试配对/直连 → 拉起 `onebridge_server` |
| 2 业务 App 拿到 binder | Shizuku 投递 → Lite `ShizukuProvider` | shell 投递 → `BridgeBinderProvider` |
| 3 授权 | Shizuku 授权弹窗 / 已授权 | `PrivilegeBridge.requestPermission`（宿主白名单，常静默） |
| 4 写系统 | `SystemApiBroker` ← `ShizukuPrivilegeBridge` | `SystemApiBroker` ← `OneBridgePrivilegeBridge` |
| 5 划掉业务 App | Shizuku 仍活，回来再 ping | `onebridge_server` 仍活，回来 wake/等 binder |
| 6 冷启 | Shizuku 自启/Watchdog | OneKuku Boot/UserPresent/WifiReady/Watchdog（对齐 V15/MINI 精华） |

门面统一：`OneKukuManager` / `PrivilegeBridges` —— Lite 与 OneIMS 业务代码同构。

## 明确不要

- 用户再装/再开 `com.onekuku.care` 或官方 Shizuku 才能用 OneIMS（onekuku）
- 把 DropIn 整仓嵌进主包冒充「融合」
- 把 OneIMS 改成第二个 Lite（依赖外置 Manager）

## 工程含义

1. **`CHANNEL_USES_EMBEDDED_BRIDGE=true` 保持**（onekuku）  
2. Care/MINI 邻仓 = **能力素材 / 编包试验田**（冷启/热路径 + server 最小面源），不是用户路径  
3. 增强点 = 旧 OneKuku + V15/MINI 冷启精华 + 内循环文案（已做「去第二 App」）  
4. **P3 引擎**：`ChannelEngine` — 默认 `ONEBRIDGE`；目标切 `CARE_MIN`（宿主内嵌 MINI server，进程 `onekuku_server`）替换旧桥  
5. 下一刀：P3a 按白名单迁入 server 最小面（见 `2026-07-30-care-min-server-import-whitelist.md`），默认不切换运行时

## 相关

- 纠偏总述：`2026-07-30-onekuku-care-home-fusion.md`  
- Lite 轻壳：`docs/changes/2026-07-16-onelink-thin-shell-shizuku-only.md`  
- 特权最小集：`docs/design/2026-07-15-onebridge-privilege-min.md`  
