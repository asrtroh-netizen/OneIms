# FNHOME Upsnap 远程唤醒可用化

日期：2026-08-03  
节点：FNHOME `192.168.2.2` · 容器 `upsnap` · 端口 **8090**（host 网络）

## 结论

- 唤醒容器 **已健康**：`seriousm4x/upsnap:latest`，`docker` 状态 healthy，HTTP `/api/health` 200。
- 设备表此前为空，已写入家网当前在线主机（待用户改名确认）：
  - `家侧-2.55` · `192.168.2.55` · `be:a0:15:d8:6c:f6`
  - `家侧-2.66` · `192.168.2.66` · `72:51:49:fe:a9:94`
- **单位 PC（本 Cursor 机 `192.168.1.7` / MAC `18:c0:4d:05:91:17`）不能作为 FNHOME WoL 目标**：不在 `192.168.2.0/24`，魔法包到不了。本机角色是 **远程打开 Upsnap 去唤醒家侧设备**。

## 本机怎么用（单位 PC）

1. 跑仓库脚本开 SSH 隧道：`scripts/fnhome-upsnap-tunnel.ps1`  
   → 浏览器打开 `http://127.0.0.1:18090/`
2. 或家局域网直连：`http://192.168.2.2:8090/`
3. 登录：超级用户邮箱 `mo@itb.one`（密码沿用你当初设的；本轮未改密、未重置）
4. 点设备「唤醒」。目标机需主板/网卡开启 **Wake-on-LAN**，关机后仍接电。

## 验证

| 项 | 结果 |
|---|---|
| `docker ps` upsnap | Up (healthy) |
| `curl http://127.0.0.1:8090/api/health` | `API is healthy` |
| 设备行 | 2 条（见上） |
| 本机发出 WoL 魔法包 | 已在 FNHOME 上对两台 MAC 广播（目标是否真开机取决于 BIOS/网卡，**未对端实测开机**） |
| 本机隧道打开页面 | 需你在本机跑隧道脚本后人工点开（可标人工） |

## 注意

- 直接写库时 `link_open` 是 select（`same_tab`/`new_tab`），不能写 `0`，否则容器会 crash loop。
- Tailscale 通后也可走 `http://<fnhome-ts-ip>:8090/`，当前 FNHOME TS 仍待授权。
- NPS 客户端本地 conf 多为模板注释，8090 隧道需在 NPS 服务端另配（本轮未改 NPS）。
