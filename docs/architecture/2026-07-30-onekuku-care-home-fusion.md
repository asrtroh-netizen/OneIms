# 2026-07-30 · 放弃内嵌特权通道 · Care 融合 OneIMS 首页

**规模**：L  
**状态**：方案冻结（本拍）· 实现分阶段  
**依据**：context-scout 侦察 + 既有 privilege-min / mini-from-v1510 / strip-candidates

## 0. 用户目标（原话对齐）

1. **放弃** OneIMS 里面的特权通道（内嵌 OneBridge / `:bridge` / `onebridge_server`）  
2. 把 **MINI 整包**（`com.onekuku.care`）**融合到 OneIMS 首页**

## 1. 推荐形态（冻结）

**方案 A（选定）**：外置 Care APK + OneIMS 首页薄壳（对齐 OneLink）

| 角色 | 职责 |
|---|---|
| `com.onekuku.care` | Manager + server + 冷启/Watchdog/无线拉起（已裁 Doctor/Hub/自动化/终端…） |
| `com.oneims.app`（onekuku） | 首页 Hero 三态 + 探测/引导/授权 + `SystemApiBroker` 写配置 |
| `:bridge` / OneBridge | **退役**（联调 PASS 后再物理删） |

**不选 B（源码整仓内嵌）**：等于换一套更大内嵌桥，违背「放弃特权通道」，爆炸半径过高。

「整包融合到首页」产品语义 = **首页就是通道入口**（状态/激活/授权），不是把 DropIn 源码塞进 `:app`。

## 2. 关键阻塞：客户端如何绑 Care

| 事实 | 证据 |
|---|---|
| Care 包名 | `com.onekuku.care` |
| Care 权限常量 | `af.shizuku.plus.permission.API_V23` |
| DropIn API 里 Manager 常量仍写 | `af.shizuku.plus.api`（`ShizukuProvider.MANAGER_APPLICATION_ID`） |
| OneLink 现用 | stock `dev.rikka.shizuku` → 默认找 `moe.shizuku.privileged.api` |
| onekuku 现用 | **内嵌 OneBridge**，未走 rikka |

→ **不能**只 `onekukuImplementation("dev.rikka.shizuku:…")` 就完事。必须三选一：

| 绑定策略 | 做法 | 取舍 |
|---|---|---|
| **B1（推荐下一刀）** | OneIMS 依赖邻仓裁过的 `api`/`provider`，并把 Manager 常量改为 `com.onekuku.care`（flavor/替换） | 与 Care 真源一致；工程稍重 |
| B2 | Care 改回官方包名 Drop-In（`moe.shizuku.privileged.api`） | 可吃 stock rikka；**违背已定 `com.onekuku.care`** |
| B3 | 自建 `CarePrivilegeBridge`（ContentProvider/自定义 attach） | 灵活；重复造轮、难维护 |

**本拍冻结假设**：走 **B1**；双 APK 可接受；OneLink 暂仍官方 Shizuku（不同步 Care，除非另令）。

## 3. 落地阶段

| 阶段 | 内容 | 完成标准 |
|---|---|---|
| **P0** 方案+探测 | 本文档；`ShizukuSetupHelper` 优先 `com.onekuku.care`；首页/深链可打开 Care | 文档+探测代码合入 |
| **P1** 契约切换 | onekuku 引入 Care 兼容 api/provider；`ChannelBridgeBootstrap` → Shizuku 系桥；`CHANNEL_USES_EMBEDDED_BRIDGE=false` | `compileOnekukuDebugKotlin`；ping+grant 真机 |
| **P2** 激活器 | `EmbeddedAdbActivator` 的 start 目标改为 Care starter（或完全交给 Care UI） | 无电脑仍能拉起 Care |
| **P3** 拆除 | 删 `:bridge` 依赖与 OneBridge*；更新 war-map | 无 onebridge_server；写配置仍 PASS |

**禁止**：P1 真机写配置未 PASS 前物理删除 `:bridge`。

## 4. 首页行为（目标态）

```
Hero INACTIVE → 未装 Care：引导安装 Care APK
             → 已装未跑：打开 Care /（可选）ADB 代拉 Care
             → 已跑未授权：requestPermission
READY        → SystemApiBroker 可写 carrier_config 等
```

品牌文案可继续叫 OneKuku；工程通道真源 = Care。

## 5. 验收账本

1. 同机：Care + OneIMS(onekuku)  
2. 首页 READY（ping + grant）  
3. 一键写 `carrier_config` 成功  
4. 划掉 OneIMS / 冷启后复连（Care Watchdog/冷启路径）  
5. 再拆 `:bridge`

## 6. 本拍已做 / 未做

| 项 | 状态 |
|---|---|
| 方案冻结文档 | 本文件 |
| Care 优先探测 | 见 `ShizukuSetupHelper` 改动 |
| B1 依赖与 Bootstrap 切换 | **未做**（下一刀） |
| 真机 | **NOT RUN** |
