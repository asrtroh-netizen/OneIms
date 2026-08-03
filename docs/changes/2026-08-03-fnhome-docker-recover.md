# 2026-08-03 · FNHOME Docker 容器找回

## 现象

用户反馈 FNHOME 上 Docker「很多东西没了」。盘点时仅剩 `ange-clashboard` / `mihomo` / `vohive(Exited)`，大量镜像已不在本地；但 `/opt/*` 数据目录与部分 compose / volume 仍在。

## 已找回（本轮）

| 容器 | 端口 | 方式 | HTTP |
|---|---|---|---|
| `mihomo` | 7890/9090 | 原本 Up | `/version` 200 |
| `ange-clashboard` | 2048 | 原本 Up | 200 |
| `vohive` | 7575 | `docker start` | 监听中 |
| `2fauth` | 8008 | `/opt/2fauth` compose up | **200** |
| `onebord` | 3000 | `/vol1/docker/onebord` compose build+up | **200** |
| `sun-panel` | **3003**（原误占 3002，已让给 AnGe-Panel） | 补写 compose + 挂原 `/opt/sunpanel` 数据 | **200** |
| `ange-panel` | **3002**（+3005） | `/opt/ange-panel` + `/root/ange-data:/data` | **200** 见 `2026-08-03-fnhome-ange-panel-restore.md` |
| `vaultwarden` | 3013 | 补写 compose + 挂原 `/opt/vaultwarden` 数据 | **200** |
| `upsnap` | host | 补写 compose + 挂原 `/opt/upsnap` | Up（health starting→跑） |

## 补写的 compose

- `/opt/sunpanel/docker-compose.yml`
- `/opt/vaultwarden/docker-compose.yml`
- `/opt/upsnap/docker-compose.yml`

（原先只有数据目录、无 compose 文件。）

## 未能按 Docker 恢复 / 未动

| 项 | 说明 |
|---|---|
| `/opt/daed` | 仅 `wing.db`，无 compose/镜像线索，未盲启 |
| 飞牛应用中心（`/var/apps`：npc/xunlei/photos…） | 不是这套 Docker compose 栈；npc 进程本就在跑 |
| 旧镜像未缓存的其它历史容器 | 无 config/镜像则无法「原样唤回」 |

## 使用入口（局域网）

- Ange：`http://192.168.2.2:2048`
- OneBord：`http://192.168.2.2:3000`
- AnGe-Panel：`http://192.168.2.2:3002`
- Sun-Panel：`http://192.168.2.2:3003`
- Vaultwarden：`http://192.168.2.2:3013`
- 2FAuth：`http://192.168.2.2:8008`
- VoHive：`http://192.168.2.2:7575`
