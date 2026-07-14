# 方案说明：不装外部 Shizuku 也能激活 OneKuku

**日期**：2026-07-14  
**状态**：切片 1 已落地（A+B 引导路径）；原生内嵌 ADB 客户端待迭代  
**规模**：L（特权通道 / 架构）

## 已拍板

用户选择：**A 内置核心组件 + B ADB 拉起**。

## 本轮已实现

| 项 | 说明 |
|---|---|
| `OneKukuCoreComponent` | 状态、内置 APK 安装、官方源下载、ADB start 命令 |
| `OneKukuAdbActivationBridge` | B 接口；当前实现 `ClipboardGuidedAdbBridge` |
| 激活入口 | 未运行时首页「立即激活」→ `prepareOneKukuCore()` |
| 排障「准备 OneKuku 核心」 | 同上；**不再跳应用市场** |
| 复制命令 | 使用 OneKuku 品牌 ADB 引导脚本 |
| FileProvider | 支持从 assets 安装 `onekuku-core.apk` |

## 诚实边界

- 为兼容现有 `rikka.shizuku` 客户端，服务端包名常量仍为官方兼容包；系统应用列表里可能仍显示上游组件名，直到后续提供换皮/自有包。
- 本轮 **未** 嵌入完整原生 ADB 客户端（LADB 级）；B 以「无线调试 + 剪贴板命令」交付，接口已预留替换。
- 未放置 `assets/onekuku-core.apk` 时走官方 Release 下载。

## 验收（人工）

1. 未装核心：点准备 → 下载/安装，不出现应用市场 Shizuku 详情页。  
2. 已装未运行：点准备 → 打开无线调试 + 复制指引。  
3. ADB 拉起后：首页激活 → 授权弹窗 → `isReady()`。  
4. `compileDebugKotlin` / 单测 `OneKukuCoreComponentTest`。
