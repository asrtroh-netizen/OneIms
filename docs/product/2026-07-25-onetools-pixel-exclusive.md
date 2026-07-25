# 2026-07-25 · OneTools 产品边界：Pixel 专属

## 拍板

用户明确：**做的是 Pixel 专属工具，不管其他厂商。**

## 含义

| 做 | 不做 |
|---|---|
| Google Pixel / 类原生 Phone 上验收 Caller（归属 + 拦截） | 小米/华为/OPPO/vivo 拨号器兼容表、分 ROM 分支 |
| CallScreening + Contacts Directory（AOSP 契约） | 厂商私有查号 SDK / 云库对接 |
| Meter 对标 Pixel Meter + Pixel/AOSP 状态栏观感（贴顶/芯片） | 「各家 OEM SystemUI」专项适配工程 |

## Caller「能否像 OEM」在本边界下的答法

这里的「像」= **像 Pixel 系统电话那套体验**（默认来电筛选角色 + 来电标签），不是像国产厂商全家桶云标记。  
Pixel 上 Directory + CallScreening 是正道；其它 ROM 显示好坏 **不纳入成功标准**。

## 文档指针

- 架构：`docs/architecture/2026-07-25-onetools-architecture-blueprint.md`
- 变更：本文件 + Caller/Meter 相关 changes
