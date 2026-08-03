# 2026-08-03 · 无 VPS 时给 Sub-Store 绑域名

背景：当前 `sub.itt.fan` 依赖阿里云 VPS 上的 NPS。VPS 到期后，需要 **不靠 VPS** 的域名入口。

## 先分清你要哪种「绑定」

| 用途 | 要不要公网任何人可访问 | 推荐 |
|---|---|---|
| 只给自己的电脑/手机/飞牛拉订阅 | 否 | **Tailscale**（最省事） |
| 公网 URL 稳定分享 / 给路由器直连下载 | 是 | **Cloudflare Tunnel** 或 **CF Workers Sub-Store** |
| 家里有公网 IP 且能做端口映射 | 是 | 路由器 443→8965（多数家宽 **CGNAT 做不到**） |

**禁止**：把 `sub.itt.fan` A 记录直接指到 `192.168.2.2`（局域网不可达外网，还会撞飞牛 nginx）。

---

## 方案 A · Tailscale（无 VPS、私有域名感）【优先推荐给「自己用」】

思路：TTFN 装 Tailscale 后得到 `100.x` / MagicDNS 名，设备进同一 tailnet 后用：

- `http://home-feiniu-ttfn:8965/` 或  
- `http://100.x.x.x:8965/`

若仍想用自己的域名（仅 tailnet 内解析）：

1. 域名 DNS 交给 Cloudflare（或任意可配私有解析的地方）  
2. 用 **分配置文件 / Split DNS / MagicDNS 额外记录**，把 `sub.itt.fan` → TTFN 的 `100.x`（只在你设备上生效）  
3. 或路由器/本机 hosts：`sub.itt.fan 100.x.x.x`

公网路人 **打不开**（这是优点）。  
进阶：Tailscale **Funnel** 可把 HTTPS 暴露到公网（相当于免费入口，但仍不如 CF 稳妥，且要开 Funnel 权限）。

---

## 方案 B · Cloudflare Tunnel（无 VPS、公网域名）【推荐给「还要公网 URL」】

在 TTFN 跑 `cloudflared`，把本机 `http://127.0.0.1:8965` 挂到 Cloudflare：

```text
sub.itt.fan ──(CF DNS CNAME)──► Cloudflare Edge
                                 └── Tunnel ──► TTFN:8965 Sub-Store
```

步骤纲要：

1. Cloudflare 把 `itt.fan`（或子域）接入  
2. Zero Trust → Networks → Tunnels → Create → 选 Docker/二进制装到 TTFN  
3. Public Hostname：`sub.itt.fan` → `http://127.0.0.1:8965`  
4. DNS：CF 自动加 CNAME（或你手动指到 tunnel）  
5. Sub-Store 开 token；需要时再加 CF Access 挡面板

不需要 VPS、不需要公网 IP、自带 HTTPS。

---

## 方案 C · Cloudflare Workers Sub-Store（无 VPS、订阅放边缘）

你们已有试点文档：`docs/changes/2026-08-03-cf-substore-*`。  
这是 **另外部署一份** Sub-Store 在 CF 上，不是给飞牛 :8965 反代。

- 优点：拉机场源更稳，飞牛只 `DIRECT` 拉 CF 下载链  
- 域名：Workers 自定义域（或 `*.workers.dev` 先测）；`sub.itt.fan` 可 CNAME 过去  
- 注意：与飞牛本地数据不同步，要迁配置/token

适合「订阅转换主站上云」；本地 `/opt/sub-store` 可留作备份。

---

## 方案 D · 家宽公网 IP + 端口转发（通常不可行）

仅当光猫/路由有独立公网 IP 时：`域名 A → 家宽 IP`，路由 443→8965，并自备证书。  
国内家宽多为 CGNAT → **直接否决**，别在这耗时间。

---

## 怎么选（结合你现状）

| 你的目标 | 选 |
|---|---|
| VPS 到期后自己设备继续用 Sub-Store | **A Tailscale** |
| 还要一个公网 `https://sub.xxx` 给任意环境 | **B Tunnel** 或 **C Workers** |
| 主要痛点是飞牛拉不动机场源 | **C Workers** 更对症 |

VPS 在期：可继续用现在的 NPS `sub.itt.fan`；无 VPS 预案按上表切换，**不要**把 DNS 改回局域网。
