# 2026-07-17 · Shizuku 分叉三连修（V15.0）

## 归属仓

`E:/GQ/One/_forks/thedjchi-Shizuku`

## 需求

1. 修正「四个变三个」（快捷入口四个小方块）
2. 版本 13.6 → V15.0
3. 去掉 Buy me a coffee

## 方案

| 项 | 根因 | 修复 |
|---|---|---|
| 四变三（快捷入口） | 非 Root 时 `tileRoot.gone`，ADB 被拉满成「两小+一宽」 | 始终 2×2 四格等宽；无 Root 时第四格禁用+说明；短标题 |
| 四变三（状态条） | `READY` 只点亮 3/4 阶段点 | `READY`/`SLEEPING` 均点亮 4 |
| 版本 | 备份文件仍为 13.6.0 | 统一 `baseVersionName = "V15.0"` |
| Donate | About `btnDonate` | 移除按钮/字符串/图标/FUNDING |

## 验证

- `:manager:compileDebugKotlin` PASS（JDK 21）
- 真机装包验收四格：**待用户**
