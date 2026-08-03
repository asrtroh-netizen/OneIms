# 2026-08-03 · 本地飞牛开启 Mihomo TUN

## 目的

让手机/电视走「网关模式」吃代理：设备网关/DNS 指向飞牛后，Telegram 等无需再填 SOCKS。

## 对照家用飞牛

| 项 | 家用 / 本地 |
|---|---|
| tun | `enable/mixed/auto-route/dns-hijack:any:53` |
| 容器 | host + `NET_ADMIN`/`NET_RAW` + `/dev/net/tun` |
| 面板/API | 本地仍为 `:2048` / `:9090` |

## 本地已落地（192.168.1.99）

| 项 | 结果 |
|---|---|
| 配置 | `/root/mihomo/config.yaml` 写入 `tun`；备份 `config.yaml.bak-before-tun` |
| DNS listen | `127.0.0.1:1053` → `0.0.0.0:1053` |
| 容器重建 | `CapAdd=NET_ADMIN,NET_RAW`，挂载 `/dev/net/tun` |
| 接口 | `Meta 198.18.0.1/30` 已出现；ip rule 2022 已注入 |
| 飞牛自身默认路由 | 仍为 `default via 192.168.1.1`（未劫持宿主出口） |

## 验证

| 检查 | 结果 |
|---|---|
| `/configs` → tun.enable | **true** |
| Google via SOCKS | **204** |
| Telegram getMe via SOCKS | **401** |
| DNS api.telegram.org | **149.154.166.110** |
| 手机网关实机 | **NOT RUN**（需用户改 WiFi） |

## 手机怎么用 TUN（推荐给电报）

1. **关掉** WiFi 里的 HTTP 手动代理  
2. **关掉** Telegram App 内 SOCKS（避免双代理）  
3. WiFi → IP 设置 → **静态**  
4. IP：同网段空闲地址（如 `192.168.1.120`）  
5. 掩码：`255.255.255.0` / 前缀 `24`  
6. **网关：`192.168.1.99`**  
7. **DNS：`192.168.1.99`**  
8. 保存后打开电报试连  

恢复：网关改回路由器 `192.168.1.1`，DNS 改回自动/路由即可。
