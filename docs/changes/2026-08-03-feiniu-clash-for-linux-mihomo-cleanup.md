# 2026-08-03 · 飞牛 TTFN 半吊子 clash-for-linux / Mihomo 清理

## 背景

媳妇家飞牛 NAS（HexHub 主机 **TTFN**，`tfs.itt.fan:4848`）前两天半截安装了 **clash-for-linux**（内核二进制为 **mihomo**），未完整卸载，残留项目目录、用户态 systemd、shell 注入与本地包装脚本。

## 盘点事实（清理前）

| 项 | 状态 |
|---|---|
| 进程 / 代理端口 | 未在跑（`NO_PROC` / `NO_PROXY_PORT`） |
| 安装根 | `/home/TTFN/clash-for-linux` ≈ **206 MiB**（含 `runtime/bin/mihomo`） |
| 配置 | `/home/TTFN/.config/clash-for-linux` |
| 入口脚本 | `/home/TTFN/.local/bin/clash*`（clashctl 等） |
| 用户 systemd | `~/.config/systemd/user/clash-for-linux.service` **enabled** |
| shell 注入 | `.bashrc` / `.profile` / `.zshrc` 含 `>>> clash-for-linux >>>` 块 |
| 包管理器包 | 无（非 dpkg/rpm 安装） |
| 关联但未删 | `/opt/ange-clashboard`（6 月起存在的 Docker 面板；当日有数据触碰，未一并拆除） |
| 明确不碰 | `/vol1/docker/onebord/**/mihomo*.js`（业务源码引用，非本机代理安装） |

## 做法

1. SSH 登录用户 `TTFN`，只读盘点确认归属。  
2. 走项目自带官方卸载：  
   `bash /home/TTFN/clash-for-linux/uninstall.sh --remove-project --yes`  
   - 停服务 / 清入口 / 删 runtime / 关闭系统代理劫持  
   - 项目目录迁到 `~/.local/share/clash-for-linux-backups/project.removed-*`  
3. 删除备份目录与空配置目录；用 awk 剥掉 shell rc 中的 clash-for-linux 块与其 PATH 行。  
4. `systemctl --user daemon-reload`（若可用）。

## 验收（清理后）

| 检查 | 结果 |
|---|---|
| `mihomo` / `clash-for-linux` 进程 | `NO_PROC` |
| 项目 / 配置 / 备份 / 包装脚本 / user unit | 均不存在 |
| shell rc 命中 `clash-for-linux\|mihomo` | `PROFILE_CLEAN` |
| 代理常用端口 7890/7891/7892/9090 | `NO_PROXY_PORT` |
| `/home/TTFN` 体积 | ≈ **28K**（由数百 MiB 级安装回落） |
| NPU 库 `/usr/lib/librknnrt.so` | 仍在（本轮未触碰） |

## 范围外发现（未修）

- `/usr/bin/rknn_server` 当前不存在、进程未运行；`librknnrt.so` 仍在。与本次 Mihomo 清理无操作交集，属既有状态漂移，需另开任务再查。  
- `/opt/ange-clashboard` 仍保留（未运行）；若也要拆，需 root + `docker compose down` 后删目录。

## 回滚

官方卸载曾把项目挪到 backup，但本轮按「清垃圾」要求已删除 backup。若需重装，只能重新 clone / 安装 clash-for-linux，无法从本机 backup 恢复。

---

## 续轮 · 全盘搜其它历史垃圾（同日）

用户要求：全盘搜索以前其它残留并删除；**目前在用的不折腾**。

### 扫描结论（在用 vs 可删）

| 对象 | 判定 | 理由 |
|---|---|---|
| Docker `ange-clashboard` :2048 | **保留** | Up 23h，在用 |
| Docker `Sub2Store` :8964/8965 | **保留** | Up 2d，`/opt/sub-store/data` 在写 |
| Docker `kspeeder` :5003/5443 | **保留** | Up 2d，`/root/kspeeder-*` 为其数据 |
| Docker `oneboard` :8866 | **保留** | healthy，业务栈 |
| `/vol1/docker/onebord/**` | **保留** | 业务源码/构建物 |
| 飞牛 `@appdata`（相册/迅雷/AI runtime/npc） | **保留** | 系统应用数据 |
| `/root/clash-for-linux`（32MiB） | **已删** | root 侧半装残留，无 unit |
| `/root/clash-for-linux-install`（42MiB，含 mihomo gz） | **已删** | 安装器/归档垃圾 |
| `/root/.cache/pip`（18MiB） | **已删** | 实验 pip 缓存 |
| `/tmp/tmp.cFvGIzPBgx`、`img2vec_smoke_stdout.log` | **已删** | 临时烟测残留 |
| `/opt/sub-store/backup/*before-restore*.tar.gz` | **已删** | 旧恢复备份；活跃数据在 `data/` |
| Docker volume `onebord-data`（LINKS=0） | **已删** | 悬挂卷；保留在用的 `onebord_onebord-data` |

### 验收

| 检查 | 结果 |
|---|---|
| `/root` 体积 | **136K**（清理前约 91MiB 级） |
| `/root` 下 clash/mihomo 路径 | 不存在 |
| 名称命中 `*clash*/*mihomo*`（home/opt/tmp/root maxdepth3） | 仅剩在用的 `/opt/ange-clashboard` |
| 四容器状态 | 均仍 Up / oneboard healthy |
