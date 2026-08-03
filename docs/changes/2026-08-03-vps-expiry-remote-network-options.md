# 2026-08-03 · 阿里云 VPS 到期后：异地组网怎么最方便

## 背景

当前枢纽：国内阿里云 VPS `8.137.155.86` = **NPS + nginx 反代**，贯穿三地飞牛（办公室 DDOS / TTFN / FNHOME）。VPS **到期**后，公网入口与反代会一起失效。

## 你真正需要的能力

1. 异地 **SSH** 进三台飞牛（含跳板进路由后台）  
2. 若干 **TCP/HTTP** 入口（Ange、Sub-Store、`*.itt.fan` 反代）  
3. 家庭侧多半 **NAT/无公网**，要靠「中心穿透」或「Mesh」

## 方案对比（按「方便」排序）

| 排序 | 方案 | 方便点 | 代价 | 适不适合你 |
|---|---|---|---|---|
| 1 | **续杯/换一台小 VPS + 原样迁 NPS** | 心智零变；域名/客户端/端口映射几乎原搬 | 持续付国内 VPS；仍是单点 | **要最少折腾 → 首选** |
| 2 | **Tailscale（或 Headscale）三地+办公PC** | 装好即组网；NAT 友好；SSH 用 100.x 直连，少开端口 | HTTP 公网域名要另想（CF Tunnel / 留一台小 VPS 只反代） | **要长期省心运维 → 强烈推荐** |
| 3 | **ZeroTier** | 类似 Tailscale，飞牛 Docker 也常见 | 生态/审核观感因人而异 | 备选 Mesh |
| 4 | **只靠易有云/LinkEase/kspeeder** | 你机器上已有 | 偏远程文件/厂商通道，**难完整替代** NPS 端口映射与自定义反代 | 可作补充，不建议当唯一骨干 |
| 5 | **纯 WireGuard 自建中心** | 可控 | 仍要一台有公网的中心；配置重于 Tailscale | 有洁癖再上 |

## 推荐结论

### 若「到期了只想最快恢复」

**再开一台廉价国内 VPS（1核1G 即可）→ 装 NPS → 把现有 npc 的 `server_addr` 指过去 → nginx 反代按原 upstream 迁。**  
域名 `*.itt.fan` A 记录改新 IP。三地 npc 配置改一行，几乎当天恢复。

### 若「想一劳永逸少养服务器」

**三地飞牛 + 办公室电脑上 Tailscale**，日常 SSH/互访走 Mesh；  
公网网站类（`sub.itt.fan`、需要给外人的 HTTP）再：

- 留一台 **更小的 VPS 只跑反代/CF**，或  
- **Cloudflare Tunnel** 挂在有服务的那台飞牛上  

这样即使「组网 VPS」没了，内网管理也不瘫。

### 不建议

- 把家宽 SSH/路由管理口直接裸映射到公网  
- 把全部宝压在单一厂商远程 App 上  

## 迁移最小步骤（续杯 NPS 路）

1. 新 VPS 装 NPS，导入/重建客户端与 TCP 映射（4848/1818/80/443…）  
2. 三地 `npc.conf` 改 `server_addr`  
3. DNS `*.itt.fan` → 新 IP  
4. nginx 反代 upstream 按旧机抄  
5. 验收：SSH 两入口、Ange、sub、办公室直连飞牛  

## 验证清单（落地后）

- [ ] `tfs.itt.fan:4848` / `hfs.itt.fan:1818` SSH  
- [ ] npc 日志 `Successful connection with server`  
- [ ] HTTP 反代关键站  
- [ ] （若上 Tailscale）三地 `tailscale status` 互通  

本笔记为选型，**未改现网**。
