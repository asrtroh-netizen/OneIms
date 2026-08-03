# 2026-08-03 · 飞牛 Mihomo + zashboard / OneBoard YAML 诊断

## 背景

用户要在飞牛 TTFN（`192.168.2.2`）装 **Mihomo + zashboard**，并附 OneBoard 编译产物 YAML（`NEW (2).yaml`）。  
同配置在 OpenClash「静态 IP + 网关旁路」可用；裸 Mihomo 只开 **7890** 时异常；电视只能网关模式；**TAG** 订阅无法更新且无延迟数字。

## 1) YAML 关键问题（按杀伤力）

| 级别 | 项 | 现状 | 影响 |
|---|---|---|---|
| P0 | `allow-lan: false` | 明确关闭 | **局域网设备（电视/手机）不能连 7890/7891**；这是「只有本机代理口、旁路却 OK」的第一嫌疑 |
| P0 | **无 TUN / redir / tproxy** | 文件中无 `tun:`、`redir-port`、`tproxy-port` | 电视只能「静态 IP + 网关」→ 需要透明网关；仅 HTTP/SOCKS 端口无法满足 |
| P0 | `dns.listen: 0.0.0.0:53` | 抢系统 DNS | 飞牛 NAS 上极易与系统/中间件冲突，导致整机解析异常 |
| P1 | `external-controller: 127.0.0.1:9090` | 仅本机 | Docker 里的 zashboard/ange-clashboard **连不上** API（除非 host 网络或改 `0.0.0.0` + secret） |
| P1 | 无 `secret` / `external-ui` | 缺失 | 面板鉴权与 UI 落点不完整 |
| P1 | 使用旧式 `port`/`socks-port` | 非 `mixed-port` | 可用，但建议统一 `mixed-port: 7890` |
| P2 | `enhanced-mode: redir-host` | 非 fake-ip | 与旁路/TUN 组合时行为不同于 OpenClash 常见 fake-ip；不是致命，但要预期一致 |
| P2 | 编译头注释「勿手改」 | OneBoard 产物 | 长期应改模块源再 `yaml:compile`；飞牛运行可用 overlay 补丁 |

本地节点段含明文密钥：**勿把该 YAML 提交公网仓库**；本诊断文档不摘录口令。

## 2) 为何「旁路网关 OK，7890 出问题」

两套完全不同的流量入口：

```text
OpenClash 旁路：
  设备网关 → 路由器/旁路机 → redir/tproxy/TUN → 规则分流 → 节点
  （设备无需会配 HTTP 代理）

裸 Mihomo 仅 7890：
  设备必须主动把 HTTP/SOCKS 指到 host:7890
  + 本 YAML 还 allow-lan:false → 局域网直接拒绝
```

所以：**同一份规则 YAML 在 OpenClash「当网关」能过电报，不代表在「仅开 7890」的 Mihomo 上局域网可用。**  
7890 异常时优先查：`allow-lan`、客户端是否真走了代理、DNS 是否被本机 `:53` 搞乱、以及节点/订阅是否为空。

## 3) 电视（只能静态 IP + 网关）怎么接

电视 **不能** 只靠 7890。飞牛侧需要三者之一：

| 方案 | 做法 | 评价 |
|---|---|---|
| **A. 推荐** | Mihomo 开 **TUN**（`tun.enable=true`，`stack: mixed/system`），电视网关=`192.168.2.2`，DNS 指向飞牛或公共 DNS（**不要**让 Mihomo 裸占 `:53` 与飞牛抢；可用 `:1053` 再由 dnsmasq 转发，或只让电视 DNS=飞牛上单独监听） | 最贴近「旁路」体验 |
| B |  upstream 路由器做策略路由，把电视网段指到飞牛 tproxy | 稳，但改路由 |
| C | 给电视上可设代理的盒子/中间设备指 7890 | 电视本体若不能设代理则不可行 |

当前飞牛 LAN：`192.168.2.2/24`，默认网关 `192.168.2.5`。  
装 TUN 前需确认内核/权限（`NET_ADMIN`、`/dev/net/tun`），Docker 部署要 `privileged` 或合适 cap + `network_mode: host`。

## 4) TAG 订阅死 / 无测速数字 — 实测根因

在飞牛上对 `http://sub.itt.fan/download/{MESL,TAG,Cyber}` 拉取：

| 订阅 | HTTP | 结果摘要 |
|---|---|---|
| MESL | **500** | `Failed to download subscription` / 无有效节点 |
| TAG | **500** | 同上（TAG） |
| Cyber | **500** | 同上 |

结论：**不是 Mihomo health-check 单独写错**；`sub.itt.fan` 聚合端当前对多个源返回 500（「订阅中不存在有效节点」类错误）。  
provider 拉空 → 组里无节点 → UI **无延迟数字**、更新按钮也像「死了」。

YAML 里 TAG 段本身结构正常（`type: http` + `proxy: DIRECT` + health-check），与 MESL 同型。  
修复优先级：**先修 sub.itt.fan / 上游 TAG 源**，再谈面板测速。

OpenClash「看起来还能用」常见于：**本地仍有旧 `providers/*.yaml` 缓存**；新装 Mihomo 冷启动无缓存就会直接暴露 500。

## 5) 与现机关系

| 组件 | 状态 |
|---|---|
| `ange-clashboard` `:2048` | 已在跑（可当 zashboard 面板） |
| `oneboard` `:8866` | 已在跑（YAML 注释显示正是 OneBoard 编译链） |
| `Sub2Store` `:8964/8965` | 已在跑 |
| Mihomo `7890/9090` | 当前未监听 |

建议：新 Mihomo 的 `external-controller` 改为 `0.0.0.0:9090` + `secret`，让现有 ange-clashboard/zashboard 指过去；或复用 oneboard 的订阅编译流水线，避免手改编译产物。

## 6) 落地前最小补丁清单（运行 overlay）

```yaml
allow-lan: true
bind-address: '*'
mixed-port: 7890          # 可替代 port/socks-port
external-controller: 0.0.0.0:9090
secret: '<set-me>'
dns:
  listen: 0.0.0.0:1053    # 绝不要在飞牛上盲抢 :53
tun:
  enable: true
  stack: mixed
  dns-hijack:
    - any:53
  auto-route: true
  auto-detect-interface: true
```

订阅未恢复前，可临时依赖 YAML 内 **本地 proxies**（自建节点）验证 TUN/电视路径；TAG 组会仍为空。

## 验证（本轮已做）

- 飞牛 `curl` 订阅：MESL/TAG/Cyber 皆 **HTTP 500**（见上）  
- 端口：`2048/8866` 占用；`7890/9090/53` 未被 Mihomo 占用  
- YAML 静态审查：`allow-lan:false`、无 TUN、DNS `:53`、controller 仅 127.0.0.1  

## 未做（待你拍板）

- ~~尚未在飞牛安装/启动 Mihomo~~ → **已部署（见续轮）**  
- 未改 sub.itt.fan 服务端（用户确认节点开关自行处理，放最后）  
- 电视实网拨测 NOT RUN（需电视改网关后做）

---

## 续轮 · 先装 Mihomo + 接面板（2026-08-03）

用户指示：TAG/MESL 节点开关他自己管，**放到最后一步**；先把机器侧能力立住。

### 已落地

| 项 | 结果 |
|---|---|
| 镜像 | `metacubex/mihomo:latest` → `v1.19.29`（aarch64） |
| 部署 | `/opt/mihomo` + `network_mode: host` + `NET_ADMIN` + `/dev/net/tun` |
| Overlay | `mixed-port:7890`、`allow-lan:true`、`external-controller:0.0.0.0:9090`、`dns.listen:1053`、`tun.enable` |
| 端口 | `*:7890` / `*:9090` / `*:1053` LISTEN |
| TUN | 接口 `Meta` `198.18.0.1/30`，ip rule 2022 已注入 |
| API | `GET /version` → `{"meta":true,"version":"v1.19.29"}` |
| 面板 | `ange-clashboard` `setup/api-list` 从 `192.168.2.5:9090` 改为 **`192.168.2.2:9090`**（secret 存 `/opt/mihomo/API_SECRET`，sqlite 已备份） |
| 面板 URL | `http://192.168.2.2:2048` |

### 电视怎么用（你这边）

1. 电视静态 IP（同网段）  
2. **网关 = `192.168.2.2`**（飞牛）  
3. DNS 可先填 `192.168.2.2` 或公共 DNS（TUN 会 hijack 53）  

### 最后一步（等你开 TAG）

在面板点订阅更新 / 测速；MESL 超额可先不管。

### 已知残留风险

- 部分 `rule-providers` 拉 `cdn.jsdelivr.net` 曾 EOF（规则集未齐）；节点通后可在面板重载  
- 本机经 7890 打 `generate_204` 曾回 502（走 DIRECT）；不影响 API/TUN 已起来的结论  
- 完整电视 YouTube 实拨：**NOT RUN**

### 双路径分工（用户确认 · 同日）

| 路径 | 用途 |
|---|---|
| **192.168.2.5:7890**（OpenClash） | **日常翻墙**，现阶段先靠它 |
| **192.168.2.2** Mihomo TUN | 电视/网关模式；面板可切换管理 |

飞牛自身默认路由仍是 `default via 192.168.2.5`；TUN 只吸「网关指向 2.2」的设备。

### 退役 2.5 的迁移策略（用户确认 · 同日）

用户明确：**2.5 后期慢慢放弃**，飞牛为终态。

| 阶段 | 做什么 | 状态 |
|---|---|---|
| 1 并存 | 面板双后端；日常可暂用 2.5:7890；飞牛 Mihomo+TUN 已就绪 | **进行中** |
| 2 切流 | 手机/电脑改指向 `192.168.2.2:7890` 或网关=2.2；开 TAG 后飞牛测速可用 | 待你开 TAG / 逐步切 |
| 3 退役 | 确认无设备依赖 2.5 后，停 OpenClash / 回收 2.5 | **未做**（按你节奏） |

面板已改为：
- **Feiniu-Mihomo(主)** `192.168.2.2:9090`（默认选中）
- **OpenClash-2.5(过渡备用)** `192.168.2.5:9090`
