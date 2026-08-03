# 三站点拓扑共识（2026-08-03 校正）

用户口述校正（以本文件为准，旧文冲突处作废）：

## 站点

| 称呼 | 是哪里 | 主力设备 | 主要职责 |
|---|---|---|---|
| **FNHOME / 狗窝** | 你家 | **飞牛 HaloXFN** `192.168.2.2` + **玩客云**（从女友家搬来） | 飞牛：mihomo / Ange / 导航；玩客云：**NPC（狗窝）+ Vohive** |
| **TTFN** | **女朋友家** | 飞牛（hostname 曾用 `home-feiniu-ttfn` 等） | Sub-Store、mihomo/TUN、npc 等；**不再是玩客云本体** |
| **办公室** | 单位 | 飞牛 DDOS 等 | 办公侧代理/Tailscale 试点 |

## 玩客云迁徙

1. 以前：玩客云在女朋友家，OpenWrt + Mihomo。  
2. 现在：玩客云 **拿到 FNHOME**，专职 **NPC + Vohive**——你认为这套是「完美」的。  
3. 因此：**不要**再在 FNHOME 飞牛 Docker 里跑第二份 Vohive/Upsnap（已删，符合「一台飞牛别碍事」）。

## FNHOME 飞牛当前 Docker（瘦身后）

- `mihomo`
- `ange-clashboard` `:2048`
- `ange-panel` `:3002`

家用 PC 远程开机：**向日葵**（无线、离路由远，不走 WoL）。

## 纠正旧表述

- ~~「Vohive 只留女朋友家玩客云」~~ → **玩客云已在 FNHOME**；女朋友家站点名是 **TTFN**（飞牛侧业务）。
