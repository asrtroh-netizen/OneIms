# Sub2Store「不走 TUN」试验结论（2026-08-03）

## 用户意图

CF Sub-Store 先停；改试「本机容器不走 Mihomo TUN」以修复拉订阅。

## 实测结论（证据）

1. **LAN Fake-IP 真源是网关 OpenClash（192.168.2.5）**，不是飞牛 Mihomo 单独作妖。  
   关闭飞牛 `dns-hijack` 后，宿主机 `nslookup @223.5.5.5` 也变成 `198.18.0.25`。
2. 飞牛 `tun.dns-hijack: any:53` + `enhanced-mode: redir-host` **反而在保护宿主机**：劫持后由飞牛给出真 IP（如 `yyds.sbyun.org → 202.155.141.21`）。
3. 给 `docker0` 加 `exclude-interface` / iptables `RETURN` 让容器 DNS **绕过飞牛劫持**后，容器会直接吃到 OpenClash Fake-IP（任意上游 DNS 都回 `198.18.0.25`）。  
   → 「容器不走 TUN」在这里会把问题变坏。
4. 去掉 `exclude-interface` 后，容器 DNS 恢复真 IP `202.155.141.21`。  
   但 `wget` 仍 `SSL EOF / Connection reset`：  
   **即便 DNS 正确，飞牛出站直连该机场 TLS 仍失败**（与早前 Cyber 500 根因一致）。

## 当前配置状态

- 已恢复：`dns-hijack: [any:53]`
- 已撤销：`exclude-interface: docker0/...`
- 已清空会害事的 docker DNS `RETURN` 规则；`/opt/mihomo/scripts/docker-dns-bypass.sh` 改为 no-op
- 仍保留：`hosts` 里 workers.dev / sub.itt.fan 条目（不影响本结论）
- CF Worker 已部署但本轮按用户意见停推进，不改 Mihomo provider

## 可行方向（按性价比）

1. **维持本地 Cyber bootstrap**（已可用）——飞牛直连机场不稳时的务实方案  
2. **OpenClash 关 Fake-IP / 改 redir-host**，并确认飞牛+容器 DNS 路径  
3. Sub2Store 出站走可用代理（需应用层代理配置，不是简单旁路 TUN）  
4. 需要边缘拉源时再启用已部署的 CF Sub-Store

## 追记（网关改回 192.168.2.1）

用户将默认网关从 OpenClash `192.168.2.5` 改回正常路由 `192.168.2.1` 后复测：

| 检查 | 结果 |
|---|---|
| `ip route` default | `via 192.168.2.1 dev lan1-ovs` |
| 宿主机拉 Cyber | **200** / 1180B / IP `202.155.141.21` |
| Sub2Store 容器拉 Cyber | **200** / 1180B / 真 IP |
| `127.0.0.1:8965/download/Cyber` | **200** / ~33KB（refresh 后） |
| 残留 docker DNS RETURN | 已删除 |

说明：上游短链本身可能只返回少量 URI；Sub-Store 聚合/缓存后体积更大。网关离开 2.5 后，OpenClash Fake-IP 污染路径消失，TLS 恢复。

## 收口（用户确认）

- 用户确认：**全好了**。
- **MESL**：当前无流量，按用户要求暂不处理。
- 复测保留：default via `192.168.2.1`；Cyber host/Sub-Store download 仍为 200。
