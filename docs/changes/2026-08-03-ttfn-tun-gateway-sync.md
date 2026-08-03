# 2026-08-03 · TTFN 同步本地飞牛 TUN 网关修复

## 目的

把本地飞牛（`192.168.1.99`）已验证的网关模式补丁同步到家用飞牛 TTFN（`192.168.2.2` / `tfs.itt.fan:4848`），使家中手机/电视可走「网关=飞牛」吃代理。

## 对照

| 项 | 本地 | TTFN |
|---|---|---|
| LAN | `192.168.1.0/24` | `192.168.2.0/24` |
| 网关/DNS | `192.168.1.99` | `192.168.2.2` |
| Mihomo 配置 | `/root/mihomo/config.yaml` | `/opt/mihomo/config/config.yaml` |
| FORWARD 脚本 | `/opt/mihomo-scripts/enable-lan-forward.sh` | `/opt/mihomo/scripts/enable-lan-forward.sh` |
| systemd | `mihomo-lan-forward.service` | 同名已 enable/active |

## 已落地

1. **FORWARD**：默认仍 `DROP`，但放行 `192.168.2.0/24` 与 `Meta` 进出。
2. **DNS53**：`PREROUTING` 将局域网 `TCP/UDP 53` `REDIRECT` → `1053`。
3. **持久化**：`mihomo-lan-forward.service` → `enable-lan-forward.sh`（enabled + active）。
4. **tun**：`enable/mixed/auto-route` + `auto-redirect: true` + `ipv6: false` + `dns-hijack: any:53`。
5. **DNS**：保留非空 `proxy-server-nameserver`（`respect-rules: true` 时不可为空）；Google / Telegram 系 `nameserver-policy` 走公共 DoH。
6. **电报组**：`📲 电报消息` 当前节点切换为 `🇺🇲 美国节点Pro`（配置内也调到首位）。
7. **备份**：`/opt/mihomo/config/config.yaml.bak-ttfn-sync`（崩溃前快照；恢复以它为源）。

## 事故与恢复

同步中曾用 `#proxy` DoH 且 `proxy-server-nameserver` 为空，触发：

`fatal: if “respect-rules” is turned on, “proxy-server-nameserver” cannot be empty`

容器 Restarting、Meta 消失。已从 `bak-ttfn-sync` 结构化恢复并补齐 tun 标志，禁止再留空 PSN。

## 验证（本轮 SSH 实跑）

| 检查 | 结果 |
|---|---|
| `docker` mihomo | Up；`Meta` 接口存在 |
| API `/version` | **200** `v1.19.29` |
| Google SOCKS `generate_204` | **204** |
| Telegram `getMe` via SOCKS | **401**（已打到官方 API） |
| `telegram.org` | **200** |
| DNS `api.telegram.org` | **149.154.166.110** |
| DNS `www.google.com` | **142.251.*** |
| 电报组 now | **🇺🇲 美国节点Pro** |
| FORWARD / NAT53 / systemd | 规则与 unit 均在 |
| 家中手机改网关实机 | **NOT RUN**（需用户在 `192.168.2.0/24` 改 WiFi） |

## 家中设备用法

1. 关掉系统 HTTP 代理与 Telegram App 内 SOCKS（避免双代理）。
2. WiFi 静态：IP 同网段空闲地址；掩码 `/24`。
3. **网关 = `192.168.2.2`**，**DNS = `192.168.2.2`**。
4. IPv6 建议「仅链路本地 / 关闭」，否则可能绕开 TUN。
5. 恢复：网关改回路由器 `192.168.2.1`，DNS 改自动即可。

## 回滚

- 配置：`cp /opt/mihomo/config/config.yaml.bak-ttfn-sync /opt/mihomo/config/config.yaml && docker restart mihomo`
- 防火墙：`systemctl stop mihomo-lan-forward.service` 后按需清规则（停服务不会自动删已插入规则，需手工或重跑脚本逆操作）。
