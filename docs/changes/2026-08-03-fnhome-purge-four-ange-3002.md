# FNHOME 删除四服务 + AnGe-Panel 仅保留 3002

日期：2026-08-03

## 删除

| 服务 | 动作 |
|---|---|
| 2fauth | `docker compose down` + 目录挪走 + 镜像 `rmi` |
| onebord | 同上（原 `/vol1/docker/onebord`） |
| sun-panel | 同上（原 `/opt/sunpanel`） |
| vaultwarden | 同上（原 `/opt/vaultwarden`） |

数据与 compose 曾暂存：`/root/deleted-bak-20260803-200846/` — **已于同日按用户要求 `rm -rf` 永久删除**（不可恢复）。

## AnGe-Panel 端口

- **为何曾有 3002+3005**：镜像默认 `http_port=3005`；你的 `/root/ange-data/conf/conf.ini` 配成 **3002**。恢复时两头都映射了，宿主机多开一个无用的 3005。
- **现况**：compose 仅 `"3002:3002"`；业务入口 `http://192.168.2.2:3002`。`docker ps` 里若仍见 `3005/tcp` 是镜像 EXPOSE 元数据，**未绑定宿主机**。

## 验证摘要

- 四容器不在 `docker ps -a`
- `:8008/:3000/:3003/:3013` 无监听；`:3002` 有，标题 AnGe-Panel
- 保留：mihomo / ange-clashboard / ange-panel / upsnap / vohive
