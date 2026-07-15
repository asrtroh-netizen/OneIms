# 2026-07-15 · 内嵌 ADB：纠正「只开热点」误导（2.0.18）

## 用户指出
PC USB adb 拉起通道不能证明出门免电脑路径；自己操作时拿不到六位码，必须靠内嵌 ADB。

## 真机证据（Pixel 9 Pro Fold）
| 状态 | `adb_wifi.tls_port` | mDNS connect | 结论 |
|---|---|---|---|
| 只开个人热点 / Wi‑Fi 未关联 AP | `0` | 无 | 无线调试未真正就绪，出不了可用端口 |
| 手机以 STA 连上电脑热点 `HALO 1424` 后重开无线调试 | `37637` | `connect=37637` | 内嵌路径可发现端口；未配对时 `AdbPairingRequiredException`（需系统六位码） |

## 修复
- 文案：明确「要连上任意 Wi‑Fi（可连电脑/别人热点）」；Pixel 上只开自己热点不够
- `OneKukuAdbMdns.isWifiClientConnected` + activate 在无端口且未 STA 时返回 `wifi_sta_required`
- 版本 `2.0.18` / code `27`

## 验证
- dumpsys / 内嵌 log 如上 PASS
- 六位码人工配对 → binder 拉起：本轮未跑完（等用户在系统页取码）
