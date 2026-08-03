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
