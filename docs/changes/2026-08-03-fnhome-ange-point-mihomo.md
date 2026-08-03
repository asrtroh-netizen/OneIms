# 2026-08-03 · FNHOME Ange 面板后端改指本机 Mihomo

## 现象

家侧网络恢复后，Ange（`ange-clashboard`）面板在跑，但后端仍指向旧旁路 `192.168.2.5:9090`。

## 本轮改动

| 项 | 值 |
|---|---|
| 面板 | `http://192.168.2.2:2048`（容器 Up，标题 AnGe-ClashBoard） |
| 默认后端 | **Feiniu-Mihomo(主)** → `http://192.168.2.2:9090`（password 空，对齐 `secret: ''`） |
| 备用 | OpenClash-2.5(过渡备用) → `192.168.2.5:9090`（保留旧 secret） |
| 数据 | `/opt/ange-clashboard/data/zashboard.sqlite`（改前已备份 `*.bak-ange-fix-*`） |
| Mihomo | `/vol1/1000/docker/mihomo` 容器 Up；`external-controller: 0.0.0.0:9090` |

## 验证

- 面板 `GET /` → **200**，标题 `AnGe-ClashBoard`
- `GET http://192.168.2.2:9090/version` → `v1.19.29`
- `setup/active-uuid` 指向飞牛条目；`setup/api-list` 首条 host=`192.168.2.2`

## 使用

家里浏览器打开：`http://192.168.2.2:2048`  
应默认连上 **Feiniu-Mihomo(主)**；若浏览器有旧缓存，强制刷新或清站点数据后再开。
