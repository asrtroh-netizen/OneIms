# 开机 / 划掉后台：激活「等半天」加速

**日期**：2026-07-15  
**规模**：Bug / M（与开机同链路）  
**关联**：`2026-07-15-swipe-kill-reactivate-verdict.md`、`2026-07-15-open-home-activate-lag.md`

## 现象

用户反馈：划掉后台再开与开机一样，「得等半天才能解决」——卡在激活/重连很久。

## 根因（耗时叠加）

| 来源 | 原值 | 影响 |
|---|---|---|
| 开机等 Wi‑Fi | 60s | 激活中长时间钉死 |
| 直连失败后再开无线调试 | 硬等 8s | 失败路径再空等 |
| SIM 就绪后短等 | 5s | 恢复前再拖 |
| 已配对仍先 mDNS 6s | 首轮必扫 | 即使 `:5555` 可用也白等 |
| connect 重试 | 3×(2s gap + connectTls 8s) | 失败路径可达数十秒 |

## 改动

1. **Boot**：`BOOT_WIFI_WAIT_MS` 60→20；`POST_WIRELESS_ENABLE_MS` 8→2.5；`POST_READY_DELAY_MS` 5→2.5  
2. **Activator**：已配对 + Wi‑Fi 已在 → **先试 `:5555`**，成功则跳过首轮 mDNS；失败回落原路径  
3. **Activator**：已配对 mDNS 3s；重试 2 次、间隔 1s；`connectTls` 4s；`tryConnectOnce` 优先 `:5555`

## 预期体感

- 杀进程重开且 tcpip:5555 仍在：**秒级**进就绪（跳过 mDNS）  
- 开机已配对、Wi‑Fi 较快：Wi‑Fi 等待上限从 1 分钟降到 20s，失败改 WAITING_WIFI 可再试  
- 无线调试未开：仍需用户/系统侧，但不能再空等 8s 伪装进度

## 验证

- 静态：调用链回放 PASS  
- **真机 PASS**（2026-07-15，Pixel 9 Pro Fold `47111FDKD0009J`，`app-debug` versionName=2.1.5 / versionCode=50）  
  - 前置：`has_paired_once=true`，`adb_wifi_enabled=1`  
  - 操作：`am force-stop` → 冷启动 `MainActivity`（模拟划掉后台）×2  
  - 证据：均出现 `fast path :5555 connected, skip first mDNS` → `OneBridge binder received`  
  - 耗时：约 **1.1～1.2s**（wake → binder）；Activity `TotalTime≈458ms`  
  - 对比：优化前失败路径可达数十秒～分钟级空等