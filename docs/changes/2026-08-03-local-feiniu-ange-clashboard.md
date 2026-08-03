# 2026-08-03 · 本地飞牛（DDOS / 192.168.1.99）安装 Ange 面板

## 背景

用户本机飞牛已装好 Mihomo，要求按家用飞牛（`192.168.2.2`）同一套方式安装并配置 **AnGe-ClashBoard**。

## 对照模板（家用飞牛）

| 项 | 家用飞牛取值 |
|---|---|
| 目录 | `/opt/ange-clashboard` |
| Compose | `compose.yaml` + `.env` |
| 镜像 | `ghcr.io/liandu2024/ange-clashboard:latest` |
| 面板端口 | `2048` |
| 数据卷 | `./data:/app/data`（`zashboard.sqlite`） |
| Mihomo API | `external-controller: 0.0.0.0:9090` + `secret` |
| 面板后端 | `setup/api-list` → 本机 IP:9090 |

## 本地落地

| 项 | 结果 |
|---|---|
| 主机 | `DDOS` / `192.168.1.99`（x86_64） |
| Mihomo | 既有容器 `mihomo`（host 网络，配置 `/root/mihomo`） |
| API 改动 | `external-controller`：`127.0.0.1:9090` → `0.0.0.0:9090`；新增 `secret` |
| Secret 落点 | `/root/mihomo/API_SECRET`（不入库） |
| 面板 | `/opt/ange-clashboard` + 容器 `ange-clashboard` |
| 面板 URL | `http://192.168.1.99:2048` |
| API URL | `http://192.168.1.99:9090` |
| 面板预置后端 | `Local-Feiniu-Mihomo` → `192.168.1.99:9090`（secret 已写入 sqlite） |

## 验证（本轮）

| 检查 | 结果 |
|---|---|
| `docker ps` ange-clashboard | Up，`0.0.0.0:2048->2048` |
| 本机/Windows 访问面板 | HTTP **200**，标题 `AnGe-ClashBoard` |
| `GET /version` + Bearer secret | `{"meta":true,"version":"v1.19.29"}` |
| 无 secret 访问 `/version` | HTTP **401** |
| `setup/api-list` 与 `API_SECRET` | **SECRET_MATCH True** |
| `/proxies` 抽样 | 有返回（本机 Mihomo 可读） |

## 使用说明

1. 浏览器打开 `http://192.168.1.99:2048`
2. 后端应已选中 **Local-Feiniu-Mihomo**；若空白，在设置里填 `http://192.168.1.99:9090`，密码读 `/root/mihomo/API_SECRET`
3. Mihomo 配置仍在 `/root/mihomo/config.yaml`；面板只管理 API，不替代订阅源配置

## 与家用差异（已知）

- 本地 Mihomo 配置在 `/root/mihomo`，不是 `/opt/mihomo`
- 本地当前 `tun.enable=false`（本轮未改 TUN；仅装面板并对接 API）
- 未同步家用那台的 OpenClash `192.168.2.5` 备用后端条目
