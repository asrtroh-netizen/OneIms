# OneTools Meter · GlassWire 能力对标路线（clean-room）

> GlassWire 为专有软件。本文只列**能力目标**与 One 侧差异化，不涉及逆向或代码移植。

## 能力矩阵

| 能力 | GlassWire | OneTools 现状 | 计划 |
|---|---|---|---|
| 实时链路速率 | 有（偏用量图） | 有（Pixel Meter 物理采样） | 保持 |
| 按应用历史用量 | 有 | **P1 已做** | 深化图表 |
| 套餐限额告警 | 有 | 无 | P2 |
| 新应用联网提示 | 有 | 无 | P3 |
| 无 Root 防火墙（本地 VPN） | 有 | 无 | P4：优先复用 One 代理栈，忌第二 VPN |
| One 家族壳 / 拦截 / 录音联动 | 无 | 有生态位 | **超越点** |

## 法律边界

- 不复制 GlassWire 源码、资源、包名、文案商标
- 仅用公开 Android API（`NetworkStatsManager`、Usage Access）
