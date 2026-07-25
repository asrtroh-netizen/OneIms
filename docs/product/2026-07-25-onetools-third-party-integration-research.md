# OneTools 候选能力调研 · aBattery / Pixel Meter

> 日期：2026-07-25  
> 视角：资深全栈架构师（许可 · 能力边界 · 集成路径）  
> 状态：历史调研（更新能力已落地为 One 自主更新中心，本文件不再讨论第三方更新器）

---

## 0. 结论摘要

| 目标 App | 开源？ | 许可 | OneTools 建议 |
|---|---|---|---|
| **aBattery** | ❌ 闭源（Play：`me.linshen.abattery`） | 专有 | **禁止拷贝**；用开源/自研替代 |
| **Pixel Meter** | ✅ | **Apache-2.0** | **可进程内集成**（优先） |

**aBattery 最接近的开源替代（推荐优先级）**：

1. **Batt**（GPL-3）— 功能心智最像；嵌入需接受 GPL 传染 → 默认不并源码  
2. **Battery Info**（Apache-2.0）— 许可友好，适合电池页底座  
3. **BatStats**（GPL-3）— 偏耗电统计，互补  

更新：使用 **One 自主更新中心**（自研），不嵌入第三方 GPL 更新器源码。

---

## 1. aBattery（闭源）→ 开源替代

见既有电池落地变更：`docs/changes/2026-07-25-onetools-battery-*`。

---

## 2. Pixel Meter

| 项 | 内容 |
|---|---|
| 许可 | **Apache-2.0** |
| 卖点 | 过滤 VPN 虚接口；通知栏/悬浮窗 |

已落地路径见 Meter 相关 changes。
