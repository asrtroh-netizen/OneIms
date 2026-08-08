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

## 回滚

```text
# Clash 恢复全局（不推荐）
# pipe PATCH /configs {"mode":"global"}

# 停掉 DDOS 桥接
ssh admin@192.168.1.99 "pkill -f ttfn_lan_bridge.py; crontab -l | grep -v ttfn_lan_bridge | crontab -"
```
