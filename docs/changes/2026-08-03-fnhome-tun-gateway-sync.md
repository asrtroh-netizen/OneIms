# 2026-08-03 · FNHOME（HaloXFN）同步 TUN 网关修复

## 目的

HexHub 主机 **FNHOME**（`hfs.itt.fan:1818` / 用户 `Halo`）在 TTFN 修好后仍缺网关能力。对齐本地飞牛与 TTFN：开启 TUN、放行 FORWARD、DNS/电报可用。

## 主机事实

| 项 | 值 |
|---|---|
| 公网入口 | `hfs.itt.fan:1818`（与 `tfs.itt.fan:4848` 同 NPS `8.137.155.86`，不同隧道） |
| 主机名 | `HaloXFN` |
| LAN | `192.168.2.2/24`，默认路由 `via 192.168.2.1` |
| Mihomo 配置 | `/vol1/1000/docker/mihomo/config.yaml` |
| 容器 | host + privileged + `CAP_NET_ADMIN` |
| DNS listen | 已是 `0.0.0.0:53`（无需 53→1053 重定向） |

## 已落地

1. **tun**：`enable/mixed/auto-route/auto-redirect/dns-hijack/ipv6:false`
2. **FORWARD**：放行 `192.168.2.0/24` 与 `Meta`；`mihomo-lan-forward.service` → `/opt/mihomo/scripts/enable-lan-forward.sh`
3. **DNS 策略**：Google / Telegram 系 `nameserver-policy` 走公共 DoH；保留非空 `proxy-server-nameserver`
4. **电报组**：`📲 电报消息` 切到 `🇺🇲 美国节点Pro`（配置首位 + API）
5. **备份**：`config.yaml.bak-fnhome-tun-*`

## 验证（本轮实跑）

| 检查 | 结果 |
|---|---|
| SSH `Halo@hfs.itt.fan:1818` | 通（hostname `HaloXFN`） |
| Meta | **UP** |
| API `/version` | **200** v1.19.29 |
| Google SOCKS | **204** |
| Telegram getMe | **401** |
| DNS telegram/google | **149.154.166.110** / **142.251.*** |
| FORWARD / systemd | 规则在；unit enabled+active |
| 家中手机改网关实机 | **NOT RUN** |

## 设备用法

网关/DNS 均填 **`192.168.2.2`**；关系统 HTTP 代理与 App 内 SOCKS；IPv6 建议仅链路本地。

## 回滚

```bash
cp /vol1/1000/docker/mihomo/config.yaml.bak-fnhome-tun-<timestamp> /vol1/1000/docker/mihomo/config.yaml
docker restart mihomo
systemctl stop mihomo-lan-forward.service   # 已插入规则需按需手工清理
```

## 备注

- 用户 `Halo` 缺家目录（`/home/Halo`），SSH 有 chdir 警告，不影响本次网关修复。
- 早期从办公室连 `1818` 曾出现无 banner/掐连，属隧道瞬时问题；稳定后凭据 `Halo` / 截图密码可用。
