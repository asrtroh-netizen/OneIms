# 2026-08-03 · 本地飞牛 Telegram「谷歌通、电报不通」

## 现象

手机走 WiFi 手动代理后：**Google 等正常，Telegram 仍不通**（与家用飞牛同类问题）。

## 根因

1. **Telegram App 常不走系统 WiFi HTTP 代理**（Android/iOS 都常见）→ 浏览器通 ≠ 电报通  
2. **DNS 污染**：默认解析 `api.telegram.org` → 错误 IP `31.13.84.2`；正确应为 `149.154.x`  
3. **策略组节点偏弱**：`📲 电报消息` 原停在 `☠️自建USHOME`，需切到 `🇺🇲 美国节点Pro`

## 本地已改（192.168.1.99）

| 项 | 操作 |
|---|---|
| DNS | `nameserver-policy` 为 `+.telegram.org` / `+.t.me` / `+.telegram.me` 走 `1.1.1.1/8.8.8.8` DoH，并 `#🇺🇲 美国节点Pro`（因 `respect-rules: true`，直连 DoH 会超时） |
| 组选择 | `📲 电报消息` → `🇺🇲 美国节点Pro` |
| 备份 | `/root/mihomo/config.yaml.bak-tg-dns` |

## 验证（本轮）

| 检查 | 结果 |
|---|---|
| `GET /dns/query?name=api.telegram.org` | **149.154.166.110** |
| `socks5h://127.0.0.1:7891` → `getMe` | HTTP **401**（到达 API） |
| `http://127.0.0.1:7890` → `getMe` | HTTP **401** |

## 手机怎么设（关键）

WiFi 代理可留给浏览器；**Telegram 要在 App 内设代理**：

1. Telegram → 设置 → 数据和存储 → 代理  
2. 类型：**SOCKS5**  
3. 服务器：`192.168.1.99`  
4. 端口：`7891`（或 `7890`）  
5. 无用户名密码 → 连接

## 同步到 TTFN

家用机此前已做过同类 DNS/组切换；若又回到 USHOME 或 DNS 漂移，按本文同样处理。
