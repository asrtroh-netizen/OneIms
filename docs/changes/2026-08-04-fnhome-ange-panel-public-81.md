# 2026-08-04 · FNHOME AnGe-Panel 公网入口恢复

节点：FNHOME / HaloXFN（`192.168.2.2`，SSH `Halo@hfs.itt.fan:1818`）

## 现象

AnGe-Panel 局域网 `http://192.168.2.2:3002` 正常，公网打不开。

## 根因

1. 本地面板一直健康：`ange-panel` Up，`:3002` → `<title>AnGe-Panel</title>`。
2. NPS「狗窝」在线；npc 日志反复：`connect to 192.168.2.2:81 ... connection refused`。
3. 此前按用户要求删除了 `/docker/npm`（Nginx Proxy Manager），宿主 **:81** 无人监听。
4. 公网侧 `8.137.155.86:81` / `hfs.itt.fan:81` 仍映射到家侧 `:81`，因此断链。

`http://hfs.itt.fan/`（无端口）仍是飞牛 fnOS（NPS→`:9999`），本来就不是 AnGe-Panel。

## 修复

更新 `/opt/ange-panel/docker-compose.yml`，在保留局域网 `3002` 的同时，把宿主端口接到面板：

- `81:3002`（对齐既有 NPS 公网隧道）
- `3003:3002`（顺带接上 VPS 仍开放的 3003；该隧道对端行为未完全恢复，见验证）

未重装完整 NPM（数据已碎、且当前只需导航面板公网可达）。

## 入口

| 用途 | URL |
|---|---|
| 公网真源域名 | `http://dh.itt.fan/`（DNS → `8.137.155.86`） |
| 公网临时可用（TCP :81） | `http://dh.itt.fan:81/` |
| 局域网 | `http://192.168.2.2:3002` |

`http://hfs.itt.fan/` 是飞牛 fnOS，不是 AnGe-Panel。

## 验证

| 检查 | 结果 |
|---|---|
| 本地 `:3002/:81/:3003` curl title | **AnGe-Panel** |
| 公网 `http://dh.itt.fan:81/` | **200** `AnGe-Panel` |
| 公网 `http://dh.itt.fan/`（:80 Host） | **502**（VPS `nginx/1.18.0`；请求未落到 FNHOME npc） |
| 公网 `:3003` | 仍 `RemoteDisconnected` |

## 2026-08-04 续 · dh.itt.fan 根路径仍 502

用户纠正：公网习惯入口是 **`dh.itt.fan`**（不是 `hfs:81`）。

证据：

- DNS `dh.itt.fan` → `8.137.155.86`
- `http://dh.itt.fan/` → VPS nginx **502 Bad Gateway**（body 含 `nginx/1.18.0 (Ubuntu)`）
- 同时刻 FNHOME `npc.log` **无**对应 dial（说明 502 发生在云上反代/NPS 主机规则，未进狗窝）
- FNHOME 本机 `Host: dh.itt.fan` → `:81/:3002` 已是 AnGe-Panel；`:80` 被飞牛 nginx 302 到 `:9999` fnOS
- 本机无 VPS SSH 密钥；NPS 控制台 `http://8.137.155.86:8888` 常见口令无法登录

要修根路径，必须改 VPS nginx 或 NPS「主机」`dh.itt.fan` 上游到狗窝 `127.0.0.1:3002`（或 `:81`）。缺 NPS/VPS 凭据时 BLOCKED。

## 2026-08-04 再续 · TTFN 侧证据与桥接

补充采证：

- TTFN（`192.168.2.2`）npc 日志曾反复：`connect to 192.168.2.2:3002 ... connection refused`（与 dh 故障时段重合）。
- 已在 TTFN 部署 `dh-ange-bridge`（`/opt/dh-ange-bridge`，nginx:alpine，`0.0.0.0:3002` → `http://8.137.155.86:81` → FNHOME AnGe-Panel）。
- TTFN 本机 `Host: dh.itt.fan` → `:3002` 已 **AnGe-Panel**。
- 但此后公网 `http://dh.itt.fan/` **仍 502**，且复测时 **TTFN/FNHOME npc 均无新的 :3002 dial** → 当前 :80 的 502 发生在 **VPS nginx 本地上游**，请求没进 NPS 客户端。

结论：家侧/旁路已就绪；根路径仍需在 **VPS nginx 或 NPS 主机规则** 把 `dh.itt.fan` 指回可用上游（TTFN `:3002` 桥或狗窝 `:3002/:81`）。

附：排查中曾误停 TTFN npc（`tfs.itt.fan:4848` 短暂不可达），已通过 Tailscale `100.64.118.44` 拉起，npc 已重新 ESTAB 到 `8.137.155.86:6666`。

## 回滚

```bash
cd /opt/ange-panel
# 仅保留 3002 映射后
docker compose up -d --force-recreate
```
