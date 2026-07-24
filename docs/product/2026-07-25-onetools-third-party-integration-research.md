# OneTools 候选能力调研 · aBattery / Pixel Meter / Obtainium

> 日期：2026-07-25  
> 视角：资深全栈架构师（许可 · 能力边界 · 集成路径）  
> 状态：调研结论 · **待拍板后再写代码**

---

## 0. 结论摘要

| 目标 App | 开源？ | 许可 | OneTools 建议 |
|---|---|---|---|
| **aBattery** | ❌ 闭源（Play：`me.linshen.abattery`，作者 Shen Lin） | 专有 | **禁止拷贝**；用开源替代实现能力 |
| **Pixel Meter** | ✅ | **Apache-2.0** | **可进程内集成**（优先） |
| **Obtainium** | ✅ | **GPL-3.0** | **默认外置唤起**；勿直接并入源码，除非接受 OneTools 整体 GPL |

**aBattery 最接近的开源替代（推荐优先级）**：

1. **Batt**（GPL-3）— 功能心智最像：Android 14 循环次数 + Shizuku 授 `BATTERY_STATS` 后可见健康/制造日/首次使用  
2. **Battery Info**（Apache-2.0）— 许可友好、Kotlin/MD3，适合作为「电池页」UI+基础指标底座  
3. **BatStats**（GPL-3）— 偏耗电统计（mAh/分 App），互补而非替代 aBattery 健康页  

---

## 1. aBattery（闭源）→ 开源替代

### 1.1 事实

- Play 名：`aBattery - Battery health` · 包名 `me.linshen.abattery`  
- 能力：Android 14 API · 健康/最大容量/循环/温度等 · Material 3 · **进阶项常需 Shizuku**（与媒体报道一致）  
- **未发现公开源码仓库** → 不得反编译集成

### 1.2 开源替代对比

| 项目 | 许可 | 与 aBattery 重合点 | 缺口 | 与 OneTools 契合 |
|---|---|---|---|---|
| [Batt](https://gitlab.com/narektor/batt) `com.porg.batt` | GPL-3.0+ | 循环次数；Shizuku/`BATTERY_STATS` 后健康、制造日、首次使用 | UI 极简；需 Android 14；View 体系 | **能力最像**；GPL 传染 |
| [Battery Info](https://github.com/DarkJoker360/BatteryInfo-app) | Apache-2.0 | 百分比/健康/温度/电压/充电/循环/容量 | Shizuku 深度能力需核代码 | **许可最佳** · Kotlin |
| [BatStats](https://github.com/mlm-games/batstats) | GPL-3.0 | Shizuku/Root 耗电明细 | 不是「电池健康一页纸」 | 可作二期「耗电分析」 |
| BatteryBot | GPL-3 | 状态栏图标/日志 | 非健康/循环导向 | 不优先 |

### 1.3 架构建议（电池）

**推荐路径 B+（许可友好 + Shizuku 深度）**：

1. 以 **Battery Info（Apache）** 或自研只读 `BatteryManager`/`BatteryProperty` 为普通权限层  
2. 进阶字段（制造日/首次使用/完整健康）参考 **Batt 的 API 用法**做 **干净室实现**（读官方 API / `BATTERY_STATS` via 已有 `ShizukuChannel`），**不复制 Batt 源码**以免 GPL 传染  
3. 挂在 OneTools 首页四态就绪后的「电池」工具卡

若接受 OneTools 整体 GPL：可直接移植 Batt UI/逻辑（仍需保留版权声明与 LICENSE）。

---

## 2. Pixel Meter

| 项 | 内容 |
|---|---|
| 仓库 | [Pixel-Tailor-CN/PixelMeter](https://github.com/Pixel-Tailor-CN/PixelMeter) / [Mystery00/PixelMeter](https://github.com/Mystery00/PixelMeter) |
| 许可 | **Apache-2.0** |
| 栈 | Kotlin · Compose · MVVM · Koin · `TrafficStats` + `ConnectivityManager` |
| 卖点 | 过滤 VPN 虚接口，避免网速翻倍；通知栏/悬浮窗；Pixel / 原生安卓 |
| minSdk | 12+（与 OneTools 31 兼容） |

### 集成建议

- **进程内 module** `:onetools-meter`（或 `feature/meter`）移植测速核心 + 通知 FGS  
- 保留 Apache 版权头与 NOTICE  
- 权限：通知 · 悬浮窗（按需）  
- UI：设置页开关，不塞进四态英雄卡  

---

## 3. Obtainium

| 项 | 内容 |
|---|---|
| 仓库 | [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) |
| 许可 | **GPL-3.0** |
| 能力 | 从 GitHub/GitLab/F-Droid/Izzy 等源安装与更新；支持国内镜像类源取决于配置 |
| 体量 | 大型 Flutter/完整更新器产品（非「几十行插件」） |

### 集成硬约束

> **把 Obtainium 源码嵌进 OneTools = OneTools 整体须按 GPL-3 开源分发。**  
> 当前 OneIMS 对外「不开放源代码」叙事下，**默认禁止进程内合并**。

### 推荐集成形态（由松到紧）

| 形态 | 做法 | 许可风险 |
|---|---|---|
| **A 外置唤起（推荐）** | 检测 `dev.imranr.obtainium*`；未装则引导下载；已装则 Intent 打开并可选预填仓库 URL | 无传染 |
| **B 配置导出** | OneTools 生成 Obtainium 可导入的 app config JSON，用户一键分享到 Obtainium | 无传染 |
| **C 自研轻量更新器** | 只服务 One 生态自有 Release（GitHub API），Apache/自有许可 | 无 Obtainium 代码 |
| D 合并源码 | 整 App GPL-3 | 高 · 需产品/法务拍板 |

---

## 4. OneTools 模块落位（拍板后）

```
onetools/
  channel/     # 已有 Shizuku 四态
  battery/     # NEW · aBattery 替代（Apache 自研/Battery Info）
  meter/       # NEW · Pixel Meter（Apache 移植）
  updates/     # NEW · 仅 Obtainium 唤起 + 自有 Release 检查（可选）
```

依赖方向：`battery` / `meter` → 可用 `channel`（进阶电池读数）；**禁止** `updates` 链入 GPL 源码树。

---

## 5. 需求八维（本调研）

| 维度 | 结论 |
|---|---|
| 🎯 表层需求 | 评估三款并找 aBattery 开源替代，意图集成进 OneTools |
| 💡 深层意图 | 用成熟开源能力快速充实工具层，同时保住 Shizuku 通道价值 |
| 📎 必须处理 | 许可墙、Shizuku 复用、与四态首页关系、Out（禁写配） |
| 🚀 查缺补漏 | GPL 传染、商店政策（悬浮窗/通知）、Pixel 专用 API、国内源可达性 |
| ✨ 顺手优化 | 电池进阶读数复用已有 `ShizukuChannel` |
| 🧠 头脑风暴 | 外置 Obtainium vs 自研轻更新；Batt 移植 vs 干净室 |
| 💩 屎山规避 | 不三份独立 App 硬塞一个 APK 无边界；不 GPL/Apache 混炖无 NOTICE |
| ⚠️ 注意事项 | aBattery 闭源；Obtainium GPL；Pixel Meter 最适合先集成 |

---

## 6. 建议下一步（请拍板）

1. 电池替代选：**干净室 + Battery Info 思路（Apache）** / 直接用 Batt（接受 GPL）  
2. Pixel Meter：**批准进程内移植**  
3. Obtainium：**外置唤起 A** / 自研轻更新 C / 整体 GPL D  
4. 实施顺序建议：`meter` → `battery` → `updates(A)`  
