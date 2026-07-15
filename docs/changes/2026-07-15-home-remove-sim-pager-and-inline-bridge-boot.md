# 2026-07-15 · 去首页白卡切卡 + 修 start.sh 断点

**版本策略**：不升 versionName / versionCode

## 1. 首页白框左右切卡

- 从 `StatusHero`（总控白/红卡）移除 `SimStatusCapsulePager`
- 删除未再使用的 `SimStatusCapsulePager` 组件
- 顶栏 `SelectedSimPill` 仍负责双卡切换（业务选卡保留）

## 2. start.sh 未写出断点

**根因**：`start.sh` 只在打开过「OneKuku 通道」时由 `BridgeApp` 写出；只装不打开则内嵌 ADB 找不到脚本。

**修复**：
- `OneKukuCoreComponent.bridgeBootShellCommand()`：与 `start.sh` 等价的内联 shell（`pm path` + `app_process`）
- `OneKukuEmbeddedAdbActivator` / `adbStartCommand` 改走内联命令，**不再依赖** `Android/data/.../start.sh`
- `BridgeLocalProvider.onCreate` 仍尽量写出 start.sh（双保险）

## 验证

- `:app:compileDebugKotlin`
- `OneKukuCoreComponentTest`
- 真机目视首页白卡无左右滑切卡；装通道后不打开也可 start（**真机 NOT RUN**）
