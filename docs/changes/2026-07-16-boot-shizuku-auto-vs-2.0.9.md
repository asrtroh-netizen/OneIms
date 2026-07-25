# 开机「Shizuku 自动活 → 自动配置」对照 2.0.8 / 2.0.9

## 用户感知

重启后好像 Shizuku 自己活了，通话配置也自动写回去。

## 事实拆解

| 环节 | 谁负责 | 2.0.8 / 2.0.9 | 现 2.3.0（OneLink） |
|---|---|---|---|
| Shizuku 守护进程启动 | **官方 Shizuku App**（开机自启 / 无线调试 / 手点 Start） | 同左 | 同左；OneIms **不能**无 ADB 静默起官方 Shizuku |
| binder 到达 → 重应用 lastApplied | `GuardService` 监听 binder | `Shizuku.OnBinderReceivedListener` → `ReapplyManager` | `PrivilegeBridges` sticky → 同语义 `BRIDGE_READY` |
| 开机主动检查/恢复 | `BootReceiver` → `OneKukuBootRestoreService` | 有（cf12fb6 已含） | 有；并补回 **旧 Wi‑Fi 连上再 enqueue** |
| 内嵌 ADB 无码直连起通道 | OneKuku 线 | 当时整包还是官方 Shizuku | 仅 OneKuku；OneLink 不走 |

## 结论

1. 「自动配置」真源是：**通道 binder 就绪后 Guard 立刻 reapply**，再加开机编排在就绪时恢复。
2. 「Shizuku 自动活」来自 **Shizuku 自身设置 / 系统无线调试**，不是 OneIms 在 2.0.9 里多写了「静默 Start」。
3. 若重启后 Shizuku 没起：OneLink 只能 `NEED_USER`（与 2.0.9 一致）；请到 Shizuku 管理器确认开机启动 / Start。
4. 中段回归过：OneLink Wi‑Fi 回调直接 return → 旧网回来也不续跑；`d7dd222` 已修。

## 验收清单（人工）

1. Shizuku 开启开机相关能力；本机记住旧 Wi‑Fi。
2. 冷重启 → 旧网连上 → Shizuku 已 Start 且已授权本 App。
3. 期望：Guard / 开机编排把 `lastApplied` 写回，无需再点首页激活。
