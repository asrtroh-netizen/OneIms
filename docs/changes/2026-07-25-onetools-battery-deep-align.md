# 2026-07-25 · 电池深度对齐 AccuBattery 缺口

## 落地

| 缺口 | 实现 |
|---|---|
| 放电历史曲线 | `battery_samples` + `BatterySparkline`；历史 Tab 点选放电会话 |
| Deep sleep % | 熄屏且几乎不掉电区间累计 / 会话时长（估算，非内核真值） |
| BatteryStats / wakelock | 「账本」Tab：Shizuku `dumpsys batterystats` 尽力解析 |

## 限制

- Shizuku `newProcess` 已标记废弃；失败时提示后续改 UserService
- Deep sleep / wakelock 随 ROM 文案变化，解析为 best-effort
- Room sessions DB 升到 v2（destructive，旧本地会话可能清空）
