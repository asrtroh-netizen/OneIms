> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

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

## 为何相对 3.0.4/3.0.5「增强反退步」

| | 3.0.4/3.0.5 | 增强后（CARE_MIN） |
|---|---|---|
| 引擎 | OneBridge / `onebridge_server` | CARE_MIN / `onekuku_server` |
| 冷启 | 立刻 `prepareOneKukuCore()` | 先 binder-only 等～15s 再 prepare |
| Ready | binder+授权 ≈ Active | binder+授权+**宿主存活** |

机制：为少弹 ADB 窗学了 V15「先等 binder」，宿主已死时却空等 → 开 App 像「还要点激活」。  
追加收敛：宿主已死时跳过 binder-only，立刻 prepare（对齐 3.0.4）。

## 重启摸测（2026-07-30 晚）

包：`3.0.9-onekuku` vc79 · Pixel 9 Pro Fold。

| 时刻 | 证据 |
|---|---|
| boot_completed | `onekuku_server` 暂无 |
| 解锁后 ~45s | `server` 有 pid、`:5555` LISTEN |
| 激活日志 | `__OB_BOOT_OK__`、`fast path :5555 connected` |
| 开 App 复连 | `host dead → prepare immediately (3.0.4-like) reason=binder_dead` |

Hero UI 字符串（Active）因 adbd 切 tcpip 导致 USB/无线会话闪断，**本轮 dump NOT RUN**；请目视确认首页 pill。

