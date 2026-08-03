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
| 公网（已验收） | `http://hfs.itt.fan:81/` 或 `http://8.137.155.86:81/` |
| 局域网 | `http://192.168.2.2:3002` |

## 验证

| 检查 | 结果 |
|---|---|
| 本地 `:3002/:81/:3003` curl title | **AnGe-Panel** |
| 公网 `http://8.137.155.86:81/` | **200** `AnGe-Panel` |
| 公网 `http://hfs.itt.fan:81/` | **200** `AnGe-Panel` |
| 公网 `:3003` | 仍 `RemoteDisconnected`（NPS 该隧道对端未对齐，不影响 :81） |

## 回滚

```bash
cd /opt/ange-panel
# 仅保留 3002 映射后
docker compose up -d --force-recreate
```
