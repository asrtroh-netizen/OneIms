# 2026-08-08 · 办公室连 TTFN：代理旁路与可达性

## 现象

开代理后登不上 `192.168.2.2:2048`（TTFN 飞牛后台）；家里静态/直连可以。用户补充 TTFN 入口：`tfs.itt.fan:4848`。

## 拓扑（既有约定）

| 节点 | 地址 | 说明 |
|---|---|---|
| TTFN（媳妇家飞牛 RK3568） | LAN `192.168.2.2`；Tailscale `100.64.118.44`；SSH NPS `tfs.itt.fan:4848` | 家中装 Mihomo（历史 `:7890`） |
| 办公室 PC（本机） | `192.168.1.7` | Clash Verge + verge-mihomo |
| 办公室飞牛 DDOS | `192.168.1.99` | DJG 只读迁移目标，与 TTFN **不是同一台** |

## 本轮采证

| 检查 | 结果 |
|---|---|
| `tfs.itt.fan:4848` | 公网 `8.137.155.86`，端口开，协议为 **SSH**（非 Web） |
| `192.168.2.2:2048`（办公室侧） | **超时**（跨站点 `192.168.1/24`→`192.168.2/24`，预期不可达） |
| `100.64.118.44:*` | **超时**；本机 Tailscale 状态 **NoState**（无 `100.x` 地址） |
| 本机系统代理 | `127.0.0.1:7897`（**不是** 7890）；旁路已含 `192.168.*` / `100.*` |
| Clash Verge 模式 | 原为 **`global`**（全局）；TUN 关闭 |
| `192.168.1.99:2048` | **200**（办公室飞牛可达） |
| SSH `TTFN@tfs.itt.fan:4848` | 端口通，本机现有密钥 **全部拒绝**（凭据在 HexHub，未入库） |

## 根因拆分

1. **办公室不能直接打家里 `192.168.2.2`**：必须走 Tailscale / NPS SSH，不是本机旁路列表能单独解决的。
2. **本机 Clash 处于全局模式**：不认规则直连；部分应用会把本应旁路的流量送进代理链（历史同类：`2026-08-03-office-pc-bad-home-proxy.md`）。
3. **Tailscale NoState**：协调服务器 HTTPS 已通，但本机未拿到 `100.x`，远端 TTFN 的 TS 入口暂时不可用。
4. **远端 Mihomo 配置**：无 SSH 凭据，本轮无法登录 TTFN 改 `/opt/mihomo/config/config.yaml`。

## 已落地（办公室 PC）

1. 经 `\\.\pipe\verge-mihomo` 将运行时 `mode: global` → **`mode: rule`**（PATCH `/configs` → 204）。
2. 持久化写入 `%APPDATA%\io.github.clash-verge-rev.clash-verge-rev\config.yaml` 与 `clash-verge.yaml` 的 `mode: rule`。
3. 系统代理保持 `127.0.0.1:7897`，旁路列表维持含 `192.168.*` / `100.*`。
4. WinHTTP 仍为「直接访问」（未指到家里 `192.168.2.2:7890`，避免重蹈 08-03 事故）。

## 续 · 速修落地（同日）

### 端口语义（易混）

| 入口 | 实际页面 |
|---|---|
| TTFN `…:9999` / 公网 `http://tn.itt.fan:9999/` | **飞牛 fnOS** |
| TTFN `…:2048`（`192.168.2.2:2048`） | **AnGe-ClashBoard**（不是 fnOS） |
| 办公室 DDOS `192.168.1.99:2048` | 本机 AnGe-ClashBoard |

### 已打通的办公室入口（含走 `127.0.0.1:7897` 代理）

| URL | 验证 |
|---|---|
| `http://tn.itt.fan:9999/` | **200** 飞牛 fnOS（直连/经 7897 皆通） |
| `http://192.168.1.99:19999/` | **200** → TS `100.64.118.44:9999` fnOS |
| `http://192.168.1.99:12048/` | **200** → TS `100.64.118.44:2048` AnGe（等价家里 `192.168.2.2:2048`） |

桥接进程：办公室飞牛 DDOS 上 `python3 /home/admin/bin/ttfn_lan_bridge.py`（`@reboot` crontab 已写）。脚本源：`scripts/ttfn_lan_bridge.py`。

### 仍待

1. 本机 Tailscale 仍 **NeedsLogin / NoState**（节点私钥被清空）；要直连 `100.64.118.44` 需托盘重登。
2. TTFN SSH（`TTFN@tfs.itt.fan:4848`）HexHub 密码为加密存储，本机现有密钥不可用；远端 Mihomo 细调仍需凭据。

## 续 · 2026-08-09 重连核验（XJ102）

会话断线后复测，**桥接与公网入口仍可用**（无需重建）：

| 检查 | 结果 |
|---|---|
| Clash 运行时 `mode` | **rule**（pipe `/configs`）；`mixed-port=7897`；配置文件已持久化 `mode: rule` |
| `http://tn.itt.fan:9999/` | 直连 / 经 `127.0.0.1:7897` 均 **200** |
| `http://192.168.1.99:19999/` | 直连 / 经 7897 均 **200**（→ TS `100.64.118.44:9999`） |
| `http://192.168.1.99:12048/` | 直连 / 经 7897 均 **200**（→ TS `100.64.118.44:2048` AnGe） |
| `tfs.itt.fan:4848` | TCP 通；banner `SSH-2.0-OpenSSH_9.2p1 Debian-2+deb12u7` |
| `192.168.2.2:2048` / `100.64.118.44:2048` | 仍超时（本机 Tailscale `BackendState=NoState`，服务 Running 但未登录） |

## 续 · 2026-08-09 「7890 打不开内网 :2048」根因与修复（DDOS）

### 现象复现（办公室飞牛 DDOS）

| 路径 | 结果 |
|---|---|
| 直连 `http://192.168.1.99:2048/` | **200** AnGe-ClashBoard |
| `curl -x http://127.0.0.1:7890 http://192.168.1.99:2048/`（TUN 开） | **502**；日志 `dial DIRECT ... i/o timeout` |
| 同代理访问 `http://192.168.1.99:12048/` | **200**（宿主 python 桥，不受影响） |
| 同代理访问 docker 网桥 IP `:2048` | **200** |

### 根因

不是「没写 DIRECT」。配置里已有 `GEOIP,LAN,DIRECT`；补上 `IP-CIDR,192.168.0.0/16` / `100.64.0.0/10` 后规则也命中，但 **mihomo TUN（`auto-route`）会把 HTTP 入站后的 DIRECT 拨号黑洞**，访问本机/TS 的 AnGe `:2048` 超时成 502。  
对照：`tun.enable=false` 后同一路径立即 **200**；仅 `route-exclude-address` 无法在本环境消除该黑洞。

### 已落地（DDOS `/root/mihomo/config.yaml`）

1. `rules` 顶部增加私网/TS `IP-CIDR … DIRECT,no-resolve`。
2. `tun.enable: false`（保留 exclude 注释便于以后再开 TUN）。
3. 备份：`config.yaml.bak-lan-direct-*` / `bak-before-tunoff-*`（主机 `/tmp` 与数据目录）。

### 验收

- DDOS 本机：`curl -x 127.0.0.1:7890 http://192.168.1.99:2048/` → **200**
- 办公室 PC：经 `192.168.1.99:7890` 访问 `:2048` → **200**
- 外网探测 `gstatic generate_204` 经 7890 仍 **204**

### 家里 TTFN（`192.168.2.2:7890` → `:2048`）

同类现象已在 **2026-08-09 XJ103** 家侧闭环（见下方「家侧内网」节）。根因不是单纯缺 DIRECT 规则，而是 **AnGe 走 Docker 端口映射时，经 mihomo TUN/mixed 访问宿主机 LAN IP 发夹 502**；TTFN 作为家用网关 **不能**照搬 DDOS「关掉 tun」。

## 续 · 2026-08-09 TTFN 设备侧复验 + 办公室 TS 重登（XJ103）

### TTFN 本机（SSH 已通）

| 检查 | 结果 |
|---|---|
| SSH `TTFN@tfs.itt.fan:4848` | **口令登录成功**（此前「密钥全拒 / HexHub 未入库」条目作废） |
| `hostname` / arch | `TTFN` · `aarch64` · up ≈8d |
| fnOS `:9999` / AnGe `:2048` / onebord `:8866` | 本机 curl 皆 **200** |
| mihomo TUN TS 排除 | 仍在：`exclude-interface: [tailscale0]` + `route-exclude-address: [100.64.0.0/10]` |
| `mihomo-lan-forward.service` | **active** |
| Tailscale 节点 | `home-feiniu-ttfn`=`100.64.118.44`；`ddos` active direct；办公室 `ddpc` 当时 offline |
| lite.gallery / imagesrv / mediasrv / trim.photos / npc | 进程在跑 |
| 磁盘 | `/` 16% · `/vol1` 6% |

> 注意：TTFN 作为家用网关 **不宜**照搬 DDOS「关掉 tun」方案；家侧继续保留 TUN + TS 排除。

### 办公室 PC（真正卡点）

| 检查 | 结果 |
|---|---|
| Clash 配置 `mode` | **rule**（`clash-verge.yaml` / `config.yaml`） |
| 桥接 `192.168.1.99:19999` / `:12048` | **200**（不依赖本机 TS） |
| 公网 `tn.itt.fan:9999` | **200** |
| 本机 Tailscale | 服务 Running，但 `PrivateNodeKey` 全 0 → 先 `NoState`，后 `NeedsLogin` |
| 已发起重登 | `tailscale up --reset --accept-dns=false` → AuthURL `https://login.tailscale.com/a/68b33b50127be`（需浏览器完成 `asrtroh@gmail.com` 授权） |

授权完成后应复验：`tailscale status` 出 `100.x`，且 `http://100.64.118.44:9999/` → **200**。

## 续 · 2026-08-09 TTFN 家侧内网（XJ103 · 与单位无关）

### 现象

家侧经 mihomo `7890` 打不开 `http://192.168.2.2:2048/`（AnGe）→ **502**；直连 `:2048` / 经代理打 `:9999` / `127.0.0.1:2048` / docker 网桥 IP 皆 **200**。

### 根因

1. **AnGe Docker publish 端口发夹**：`network_mode` 原为 bridge + `0.0.0.0:2048->2048`，经 TUN/mixed 访问宿主机 LAN IP 时 DNAT 路径黑洞。  
2. **仅加 `route-exclude` + LAN `DIRECT` 不够**（与 DDOS 结论一致），但 TTFN 需保留 TUN 作家用网关，故不关 tun。  
3. 局域网访问 `tn/fn.itt.fan` 仍解析公网 `8.137.155.86`，大文件会 NAT 回环变慢（08-04 已记录）。

### 已落地（TTFN）

1. **AnGe → `network_mode: host`**  
   - 运行态：`/home/TTFN/ange-clashboard-hostnet/compose.yaml` 重建容器  
   - 持久化：`/opt/ange-clashboard/compose.yaml` 同步为 host（备份 `compose.yaml.bak-bridge-*`）  
2. **mihomo**（`/opt/mihomo/config/config.yaml`，备份在 `/home/TTFN/config.yaml.bak-lan-intranet-*`）  
   - `tun.route-exclude-address` 增补 `192.168.0.0/16` / `10.0.0.0/8` / `172.16.0.0/12`  
   - `rules` 顶部增补私网/TS/loopback `IP-CIDR … DIRECT,no-resolve`  
   - `hosts`：`tn.itt.fan` / `fn.itt.fan` → `192.168.2.2`  
   - **未**关闭 `tun.enable`  
3. **AdGuardHome split DNS**  
   - `rewrites`：`tn.itt.fan` / `fn.itt.fan` → `192.168.2.2`（`enabled: true`）  
   - 曾因 HUP/杀进程导致 AGH 掉线；已用 root `nsenter` + `appcenter-cli start AdGuardHome` 拉起

### 验收

| 检查 | 结果 |
|---|---|
| `curl -x 127.0.0.1:7890 http://192.168.2.2:2048/` | **200** |
| `curl -x 127.0.0.1:7890 http://www.gstatic.com/generate_204` | **204**（代理出海仍可用） |
| `nslookup tn.itt.fan 127.0.0.1` | **192.168.2.2** |
| `nslookup fn.itt.fan 127.0.0.1` | **192.168.2.2** |
| `docker inspect ange-clashboard` NetworkMode | **host** |
| AdGuardHome `:53` | 在听（pid 挂在 init） |

### 回滚（家侧）

```bash
# AnGe 恢复 bridge
cp -a /opt/ange-clashboard/compose.yaml.bak-bridge-* /opt/ange-clashboard/compose.yaml   # 选对应备份
cd /opt/ange-clashboard && docker compose up -d

# mihomo 恢复
cp -a /home/TTFN/config.yaml.bak-lan-intranet-* /opt/mihomo/config/config.yaml
docker restart mihomo

# AdGuard rewrite：面板删 rewrite，或恢复 AdGuardHome.yaml.bak-lan-intranet-*
nsenter -t 1 -m -u -i -n -p appcenter-cli start AdGuardHome
```

## 回滚

```text
# Clash 恢复全局（不推荐）
# pipe PATCH /configs {"mode":"global"}

# 停掉 DDOS 桥接
ssh admin@192.168.1.99 "pkill -f ttfn_lan_bridge.py; crontab -l | grep -v ttfn_lan_bridge | crontab -"
```
