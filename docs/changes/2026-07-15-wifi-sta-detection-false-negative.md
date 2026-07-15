# 修：旧 WiFi 已连仍误判未 STA → 卡激活中 / WAITING_WIFI

## 现象

已配对设备连上记住的旧 Wi‑Fi 后，首页一直「激活中」/ hint=`WAITING_WIFI`，与「旧网无码静默就绪」预期不符。

## 根因

`OneKukuAdbMdns.isWifiClientConnected` 在 `WifiInfo.networkId < 0` 时直接返回 false。  
无定位 / NEARBY 权限时隐私限制常使 `networkId=-1`，即使系统侧 WIFI CONNECTED 也会假阴性；`BootReceiver` 重试同一检测 → 永久等待。

真机日志：

```
paired device: wait Wi‑Fi before mDNS
boot ui hint=WAITING_WIFI
```

同时 `cmd wifi` / `dumpsys connectivity` 显示已连旧网。

## 修复

- 优先用 `ConnectivityManager`：任意网络 `TRANSPORT_WIFI` + `INTERNET` 即视为 STA
- `WifiManager` 兜底：`COMPLETED`/`ASSOCIATED` 即使 `networkId<0` 也算已连

## 验证

- 编译 / 装包后：旧网已连时不应再打 `wait Wi‑Fi before mDNS`，hint 应离开 `WAITING_WIFI`
