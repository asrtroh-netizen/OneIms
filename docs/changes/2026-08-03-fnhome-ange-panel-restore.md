# FNHOME 恢复 AnGe-Panel（与 Sun-Panel 端口对齐）

日期：2026-08-03  
节点：FNHOME / HaloXFN（`192.168.2.2`，SSH `Halo@hfs.itt.fan:1818`）

## 背景

Docker 恢复批次里误把 `hslr/sun-panel` 占到了历史 **3002**。用户反馈下午还在用「类似 sunpanel」的面板——经排查是 **AnGe-Panel**（`ghcr.io/liandu2024/ange-panel`），数据在 `/root/ange-data`（`database.db` 下午约 15:47 有写入）。

## 变更

| 服务 | 入口 | Compose | 数据 |
|---|---|---|---|
| **ange-panel** | `http://192.168.2.2:3002`（标题 AnGe-Panel） | `/opt/ange-panel/docker-compose.yml` | `/root/ange-data` → **`/data`**（entrypoint 默认 `ANGE_DATA_DIR=/data`） |
| **sun-panel** | `http://192.168.2.2:3003`（标题 Sun-Panel） | `/opt/sunpanel/docker-compose.yml` 端口改为 `3003:3002` | `/opt/sunpanel/{conf.ini,database,uploads}` |

额外映射 `3005:3005` 保留（镜像默认端口）；当前 conf 为 `http_port=3002`，业务入口以 **3002** 为准。

## 关键踩坑

1. 初次把数据挂到 `/app/data` 时，容器仍用空的匿名 `/data`，会生成新库；必须挂 **`/root/ange-data:/data`**。
2. 远端 `bash -lc` + 内嵌 heredoc 的 `\n` 会变成字面量，改端口/写脚本请用 **SFTP 上传 `.py` 再 `python3 /tmp/...`**。

## 验证（本机经 SSH）

- `docker ps`：`ange-panel` Up，`0.0.0.0:3002->3002` + `3005`；`sun-panel` Up，`0.0.0.0:3003->3002`
- `curl http://127.0.0.1:3002/` → `<title>AnGe-Panel</title>`
- `curl http://127.0.0.1:3003/` → `<title>Sun-Panel</title>`
- `/data/database/database.db` 大小 659456，与宿主机 `/root/ange-data/database/database.db` 一致

## 回滚

- sun-panel：compose 改回 `"3002:3002"` 后 `docker compose up -d`（会与 ange-panel 冲突，需先停 ange-panel）
- ange-panel：`cd /opt/ange-panel && docker compose down`
