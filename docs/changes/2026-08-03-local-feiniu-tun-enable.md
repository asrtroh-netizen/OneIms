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
4. IP：同网段空闲地址（如 `192.168.1.77`）  
5. 掩码：`255.255.255.0` / 前缀 `24`  
6. **网关：`192.168.1.99`**  
7. **DNS：`192.168.1.99`**  
8. 建议将 **IPv6 关掉/仅链路本地**（否则谷歌可能走坏掉的 v6）  
9. 保存后打开电报 / 谷歌试连  

恢复：网关改回路由器 `192.168.1.1`，DNS 改回自动/路由即可。

## 网关模式踩坑（同日修复）

手机设置正确后仍「谷歌上不了」：飞牛 `FORWARD` 默认 **DROP**，局域网经网关转发的包被丢掉（计数曾见 2152 packets）。

已处理：

- `iptables` 放行 `192.168.1.0/24` 与 `Meta` 进出  
- `sysctl` 保持 `ip_forward=1`、`rp_filter=0`  
- systemd：`mihomo-lan-forward.service` → `/opt/mihomo-scripts/enable-lan-forward.sh`  
- **DNS**：局域网 `UDP/TCP 53` → `REDIRECT 1053`（否则手机 DNS=`192.168.1.99` 会 connection refused）  
- `tun.auto-redirect: true`、`tun.ipv6: false`  
- Google 系域名 `nameserver-policy` 走美国节点 DoH（避免解析成污染 IP）  

LAN 侧验证（Windows → `192.168.1.99`）：`www.google.com`→`142.251.*`，`api.telegram.org`→`149.154.166.110`。
