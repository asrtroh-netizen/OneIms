# 变更说明 · 删除 eSIM / LPA 精简功能

日期：2026-07-13  
范围：MainActivity overlay、实验页/设置页入口、UiModels 契约、Manifest receiver、中英文案、README/USAGE、空 `core/lpa` 目录

## 做了什么

1. 移除 `AppOverlay.ESIM` 与 `EsimScreen` 接线；主导航恢复为直接 `Crossfade`。
2. 删除实验功能页与设置「工具」区的 eSIM 入口及 `onOpenEsimManager` 契约。
3. 删除 `EsimOperationCallbackReceiver` 的 Manifest 注册。
4. 清理全部 `experimental_esim_*` / `esim_*` 字符串；保留通用 `action_back`；工具区副标题去掉 eSIM 表述。
5. 同步 README / USAGE；清空已无源文件的 `core/lpa` 目录。

## 未改动

- 控制中心切卡（`DataSimSwitch*` / `getActiveSims`）
- APN 选卡（`activeSim`）
- 5G 显示（`resolveSimpleFiveGDisplay` 等）

## 背景

上一轮已删除 `EsimScreen` / `EsimLpaManager` 等实现源，但入口与引用未清，处于半残状态。本轮按「宁缺毋滥」完成收尾清扫。
