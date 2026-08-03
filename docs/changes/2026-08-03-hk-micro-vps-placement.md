# 香港微型服务器落位建议（相对 Cloudflare）

日期：2026-08-03  
决策：用户拟购买香港微型 VPS，用于跑轻量服务（替代/补充 CF Sub-Store 思路）。

## 角色分工（推荐）

| 位置 | 负责 | 不负责 |
|---|---|---|
| **飞牛 NAS** | 家庭 TUN 网关、DNS、TV 网关、面板、日常代理入口 | 去拉被墙的机场订阅源 |
| **香港小鸡** | Sub-Store（或轻量订阅转换）、必要时小反代/健康检查 | 别把全家流量都绕去港机 |
| **192.168.2.5 OpenClash** | 过渡期日常出口 | 逐步退役 |

数据流：

```text
机场源 ──(港机直连拉取)──► 港机 Sub-Store
                              │
                              ▼ https://sub.xxx/download/...
                     飞牛 Mihomo providers (proxy: DIRECT)
                              │
                              ▼ 家宽 TUN / 7890 / TV
```

## 买机最小规格（够跑小东西）

- 1 vCPU / 512MB~1GB RAM / 10~20GB SSD  
- 月流量 500GB+ 通常足够（只拉订阅几乎不吃流量）  
- 系统：Debian 12 / Ubuntu 22.04  
- 网络：要真·香港线路；避坑「假香港 / 被墙 IP」  
- 安全：只开 22（密钥）+ 443；Sub-Store 务必 token，禁止裸奔

## 港机上建议只跑

1. **Docker Sub-Store**（或你熟悉的轻量订阅后端）——主用途  
2. 可选：Caddy/Nginx 反代 + HTTPS  
3. 可选：极简 uptime 探测（看订阅 URL 是否 200）

**先别往港机堆**：全家代理中转、大硬盘网盘、Immich、整站业务。飞牛才是家用中枢。

## 和现网衔接

1. 港机 Sub-Store 配好 Cyber/TAG…  
2. 飞牛 `/opt/mihomo/config/config.yaml` 里 provider URL 改为港机下载地址，`proxy: DIRECT`  
3. 订阅域名直连规则可改为港机域名（或继续直连港机 IP）  
4. `sub.itt.fan`：可 NPC/反代到港机，或新域名；未验证前不要砍飞牛本地 Sub-Store  
5. 飞牛侧 Fake-IP 保持关闭（redir-host）；订源强制直连约定保留

## 验收清单（买回来后）

- [ ] 港机 `curl` 机场源 URL → 200  
- [ ] 港机 `/download/Cyber?target=ClashMeta` → 有节点  
- [ ] 飞牛 Mihomo provider 更新 → count > 0  
- [ ] 面板延迟/YouTube/TV 网关正常  
- [ ] 飞牛本地 Sub-Store 可降级为备份或下线

## 相对 CF 的取舍

| | 港机 VPS | Cloudflare Workers |
|---|---|---|
| 拉源稳定性 | 通常更好（真服务器 IP） | 可能被机场拦 ASN |
| 运维 | 要自己照看系统 | 近乎无运维 |
| 成本 | 月费固定 | 免费档有限额 |
| 扩展 | 还能挂点小工具 | 偏纯 API |

你的场景（家里 NAS + 要稳定拉订阅）→ **港机更贴。**

## 购买推荐（2026-08 选型口径）

> 价格库存常变，以下按「档位 + 商家类型」推荐，下单前以官网实时套餐为准。

### 你的用途刚好很轻

只跑 Sub-Store / 小反代 → **1C1G / 20GB / 月流量 ≥500GB** 足够；不必上贵的「家宽落地 / 大带宽」。  
飞牛访问港机下载订阅，内地→香港延迟正常即可；**真正要稳的是港机出站去拉机场源**。

### 三档怎么选

| 档位 | 适合谁 | 方向（类型） | 说明 |
|---|---|---|---|
| A 试错 | 先验证思路 | 香港小商家月付 KVM 小鸡（常见 1C1G） | 月付，不行就弃；先验真香港 IP、能 Docker |
| B 稳妥（更推荐） | 打算长期挂着 | **搬瓦工（BandwagonHost）香港** 或 **DMIT 香港入门** | 贵一点但口碑/线路可控，少踩假香港 |
| C 大厂备选 | 怕小商跑路 | DO/Vultr/Linode 选 Hong Kong | 贵、有时缺货；胜在面板与工单 |

**我个人更倾向：B 档入门港区**——你这不是练手博客，是家里代理链路的一环，别为了省 20 块买到假港/超售鸡。

### 下单硬条件（不满足就别买）

1. **KVM + 独立 IPv4 + root**（别买 NAT/仅 IPv6）  
2. 标明 **Hong Kong** 机房；到手用 `curl ip.sb` / whois 核验 ASN 是否真港  
3. 能装 Docker；内存 **≥1GB**（512MB 勉强，加 swap 也心慌）  
4. 月付优先；支持快照/重装  
5. 只要 22/443，Sub-Store 必须 token，禁止面板裸奔公网

### 明确不推荐（对你这个场景）

- 「无限流量大盘鸡」——你用不上，还容易超售卡死  
- 只宣传 CN2/GIA 天价套餐——拉订阅用不到这个溢价  
- 共享 IP / 被刷黑的便宜货——机场源可能直接连你都拒  
- 把港机当全家代理中转——家用出口继续飞牛 TUN

### 到手 10 分钟验收

```bash
curl -4 ifconfig.me / curl -4 ip.sb     # 应是香港
docker run --rm hello-world             # 能跑容器
curl -I https://www.google.com          # 港机出站正常
# 再装 Sub-Store，curl 你的机场源 URL 应 200
```
