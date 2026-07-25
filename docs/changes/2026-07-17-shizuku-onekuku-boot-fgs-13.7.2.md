# Shizuku 13.7.2-asrtroh：对齐 OneKuku 开机编排（保留 TCP）

## 动机

OneKuku 真机已验证能完美自启。13.7.1 只搬了 waitForWifi，缺少 OneKuku 真正关键的 **Boot FGS + NETWORK_STATE 再试**。

## 落地（对标）

| OneKuku | Shizuku 13.7.2 |
|---|---|
| `BootReceiver` + `goAsync` | `BootCompleteReceiver` |
| `OneKukuBootRestoreService` FGS | `BootAdbStartService` FGS |
| 等解锁 → 等 Wi‑Fi → `ensureAdbWifi` | 同序；**TCP 模式跳过 Wi‑Fi** |
| `NETWORK_STATE_CHANGED` 再 enqueue | 同；无 FGS 白名单时走 WorkManager |

## 产物

https://github.com/asrtroh-netizen/shizuku/releases/tag/v13.7.2-asrtroh
