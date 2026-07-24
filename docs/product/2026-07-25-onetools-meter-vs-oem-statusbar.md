# OneTools Meter · OEM 状态栏网速对标

> 可比清单第 2 项：**各 OEM 系统自带状态栏网速**（小米 / 华为 / OPPO / vivo 等），不是 GlassWire。

## 边界

厂商把速率画进 **SystemUI**；普通应用无系统签名，无法 1:1 植入状态栏。逼近路径：

| 手段 | 适用 | 说明 |
|---|---|---|
| Android 16 Live Update chip | API 36+ | `setRequestPromotedOngoing` + `setShortCriticalText`（≤7 字） |
| 悬浮窗贴顶右侧 | API 31+（本模块 min） | 「贴到 OEM 位」一键靠右上状态栏槽位 |
| 通知栏常驻 + 动态图标 | 全版本 | 已有 Pixel Meter 对标能力 |

## 超越点（相对 OEM）

- 物理链路采样，忽略 VPN 双计
- One 家族主题 / bit·s 单位 / QS Tile / 显示模式
- 不依赖厂商 ROM 开关，跨 OEM 一致

## 验证注意

状态栏 chip 是否出现由系统决定（空间、是否提升 ongoing）；模拟器常不展示，需真机 Android 16+。
