# 开机自启发热风险审查

日期：2026-07-19  
范围：OneIMS 开机恢复链 + 对照仓 HSSkyBoy-Shizuku（r11 冷启路径）  
结论：**成功冷启后持续发热风险低**；失败/无 Wi‑Fi/无线调试常开等异常路径有中等风险。

## 结论摘要

| 路径 | 常态（已成功） | 异常态 | 严重度 |
|---|---|---|---|
| Shizuku `SelfStarterService` | 短时 FGS，成功后停 | 最长约 120s、400ms 端口轮询 + mDNS | P2（瞬时） |
| Shizuku `WirelessBootStartWorker` | 成功即停 | Wi‑Fi 永不就绪时 `Result.retry()` 指数退避永续 | P2 |
| Shizuku `WatchdogService` | 事件监听 FGS，无忙轮询 | binder 反复死亡可连拉 SelfStarter（≤5） | P3 / 抖动时 P2 |
| 无线调试保持开启 | — | `adb_wifi` 开机后默认不关，Wi‑Fi 侧功耗偏高 | P2（续航/温升） |
| OneIMS `GuardService` | 120s 轻量巡检 | IMS 持续未注册则每 2 分钟 reapply | P3 / 抖动时 P2 |
| OneIMS 开机恢复 FGS | 有界等待后 `stopSelf` | WAITING_WIFI 靠广播再入队 | P3 |

未发现：WakeLock 长持、死循环忙等、亚秒级后台热循环（UI 前台 1s 轮询除外）。

## 证据指针

- `WirelessBootStartWorker`：Wi‑Fi 等待路径 `Result.retry()` + `BackoffPolicy.EXPONENTIAL` 10s 起
- `SelfStarterService`：`PORT_WAIT_TIMEOUT_MS=120_000`，`PORT_POLL_INTERVAL_MS=400`
- `WatchdogService`：binder 回调驱动，无定时巡检循环
- OneIMS `GuardService`：`INTERVAL_MS=120_000`；`OneKukuBootRestoreService` 跑完 `stopSelf`
- OneIMS `MainActivity`：`while(true)+delay(1000)` 仅 UI 存活期

## 人工验证建议

1. 冷启成功后静置 10–15 分钟，看电池统计里 Shizuku / OneIMS / 无线调试占比与机身温度。
2. 对比：关 Watchdog、关开机无线自启、手动关掉无线调试后再测温。
3. 无 Wi‑Fi 冷启：确认 Worker 是否长时间反复唤醒（logcat `Wireless boot worker waiting`）。
