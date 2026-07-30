# CARE_MIN：冷开 Active 闪「未激活」修复

日期：2026-07-30

## 现象

解锁后打开 App：需点激活 → 短暂 Active → 立刻变回「未激活」→ 再等一会才稳定 Active。  
期望：冷开自动走恢复，中间显示「激活中」，不要闪回「未激活」诱使手动点。

## 根因

1. 已配对冷启 / binder_dead / 前台复连故意**不置** `CONNECTING`，Hero 在 `isReady()=false` 时落到 `INACTIVE`。
2. CARE_MIN 下外置 binder 短暂回弹即可让 `bridgeReady=true`，**盖掉** `CONNECTING`，先标 Active；宿主 `onekuku_server` 未稳或 binder 再掉 → 立刻变「未激活」。

## 修复（`MainActivity.kt`）

- Hero：`bridgeReady` 额外要求 `OneKukuHostServerBootstrap.isHostServerAlive()`（CARE_MIN）。
- binder 已回但宿主未稳：卡片兜底 `ACTIVATING`；`settle` / `syncPrivilegeUi` 不发「已激活」。
- 已配对 `prepareOneKukuCore`、`schedulePrivilegeReconnectShots`、`binder_dead`：复连窗钉 `CONNECTING`。
- 复连判定与耗尽：以 binder+宿主双真为准；耗尽后放开 `CONNECTING`。

## 验证

- `assembleOnekukuDebug` + `adb install -r` → Success（`3.0.9-onekuku` vc79）。
- 杀 `onekuku_server` 后冷开：UI 为「正在激活 OneKuku」（非「未激活」）→ 稍后稳定 `Active`，`onekuku_server` 存活。
