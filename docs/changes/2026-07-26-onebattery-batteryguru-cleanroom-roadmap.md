# 变更说明 · OneBattery 对齐 Battery Guru 公开能力（干净室路线图）

**日期**：2026-07-26  
**状态**：架构定稿 · 待切片落地  
**规模**：L（产品能力对位；单仓 `:onebattery`）

## 1. 需求解读

用户口语「妇科Better GuRU」→ **复刻 Battery Guru**（`com.paget96.batteryguru` 级体验）。

权威约束（既有，不推翻）：

- `docs/changes/2026-07-26-onebattery-standalone-seed.md`
  - **不做**反编译 / 源码级克隆 `com.paget96.batteryguru`
  - **不做**商标名 Battery Guru
  - **不做** AOD 全功能（首期）
- 实现路径：干净室自研，能力集合可参考公开商店描述 + 本仓 AccuBattery 思路历史

## 2. 当前基线（已验证）

模块：`:onebattery` · `applicationId=com.onebattery.app` · 品牌 **OneBattery**

| 域 | 现状 |
|---|---|
| 实时仪表 | 电量 / 温度 / 电压 / 电流 / 状态 / 电源 |
| 健康 | 充电会话估容量 + 健康度 + Pixel 设计容量预设 |
| 闹钟 | 充电阈值闹钟（默认 80%） |
| 耗电 | 分应用估算（用量访问） |
| 历史 | 充/放电会话 + Sparkline + Deep sleep 估算 |
| 账本 | Shizuku `dumpsys batterystats` / wakelock 尽力解析 |
| Widget | 桌面小组件（电量·状态·温度） |
| 通道 | Shizuku UserService + 回退 |

验证（本轮）：

```text
.\gradlew :onebattery:assembleDebug
→ exit 0
产物：onebattery/build/outputs/apk/debug/onebattery-debug.apk
```

## 3. 公开能力差距矩阵（相对 Battery Guru Play 描述）

| # | Guru 公开能力 | OneBattery | 分期建议 |
|---|---|---|---|
| G1 | 实时 mA / mV / 功率 W | 有 mA/mV；**缺 W** | P0 |
| G2 | 充满 ETA（亮屏/熄屏分列） | 有粗 ETA；**未分列** | P0 |
| G3 | 充电会话历史 + 容量 | ✅ | 已有 |
| G4 | 充电器/线材实测对比 | ❌ | P1 |
| G5 | 亮屏/熄屏/awake/deep sleep | 部分（deep sleep 估算） | P1 补齐 |
| G6 | 前台分应用耗电 | ✅ 估算 | 已有 |
| G7 | Wakelock 监视 | ✅ best-effort | 已有 |
| G8 | 异常耗电告警 | ❌ | P1 |
| G9 | 温度 24h 曲线 + 高低温闹钟 | 实时温度有；**曲线/闹钟缺** | P0 |
| G10 | 充满闹钟 | ✅ | 已有 |
| G11 | 低电量闹钟 | ❌ | P0 |
| G12 | 状态栏常驻通知（可定制图标） | 仅跟踪 FGS / 闹钟 | P0 |
| G13 | DND 时段尊重闹钟 | ❌ | P2 |
| G14 | AOD 锁屏电池 HUD | ❌（种子明确不做全功能） | **OUT / 远期** |
| G15 | 悬浮层 overlay | ❌ | P1 |
| G16 | 多形态 Widget / 带时钟 | 单 Widget | P1 |
| G17 | M3 信息架构与动效 | 功能 Tab 可用；体验偏工具 | P1 体验 |

## 4. 架构边界（不变式）

```
UI (Compose BatteryScreen tabs)
  → Prefs / SessionStore / AppDrainStore (Room)
  → BatteryReader / CapacityEstimator / DrainMath
  → BatteryChargeService (FGS 会话)
  → ShizukuChannel → ShellBatteryService (dumpsys)
  → WidgetProvider / Alarm
```

**禁止**：

- 依赖反编译产物或拷贝 Guru 资源/包名/商标
- 把 AOD 全功能塞进 P0（种子 OUT）
- 在 `:app` / `:onetools` 再嵌一套平行电池域（单一真源在 `:onebattery`）

**允许**：

- 公开商店功能列表对位
- Apache-2.0 Battery Info 类能力集合（已有 NOTICE 口径）
- Shizuku / UsageStats 系统能力

## 5. 推荐执行切片（下一刀）

**P0 · 「Guru 体感最小闭环」**（建议先做）：

1. 功率 W = `V × I` 展示  
2. 低电量闹钟 + 温度高/低闹钟（复用通知通道模式）  
3. 可选常驻状态栏通知（电量% / 温度）  
4. ETA 亮屏/熄屏分列（基于会话采样速率）

**P1**：充电器对比账本、异常耗电告警、悬浮层、Widget 变体、温度 24h 曲线  
**P2**：DND 日程、更细报警频率、体验动效  
**OUT**：AOD 全功能、商标名、APK 逆向克隆

## 6. 验收门禁

- `.\gradlew :onebattery:assembleDebug` 绿
- 新增闹钟/通知路径有空态与权限降级文案
- 变更说明同步；不引入 `batteryguru` 包名/字符串商标
