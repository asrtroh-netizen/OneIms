# FNHOME 再瘦身：移除 Upsnap / Vohive

日期：2026-08-03

## 决策

- 家用机远程开机改走 **向日葵**（无线 + 离路由远，WoL/Upsnap 不合适）。
- **Vohive** 跑在 **FNHOME 的玩客云**上（从女友家迁来，兼 NPC）；**不要**再在本台飞牛 Docker 里跑第二份。
- 站点名 **TTFN = 女朋友家**（现为飞牛业务，不是玩客云本体）。详见 `docs/architecture/2026-08-03-three-site-ttfn-fnhome-wankeyun.md`。
- FNHOME Docker 目标：**少而清**。

## 已删除

| 项 | 动作 |
|---|---|
| `upsnap` | compose down + `rm -rf /opt/upsnap` + `rmi seriousm4x/upsnap` |
| `vohive` | `docker rm -f` + `rm -rf /docker/vohive` + `rmi iniwex/vohive` |
| `/root/build-qmi-wwan.sh` | 一并删除 |

`:8090` / `:7575` 已无监听。

## 当前保留容器

- `mihomo`
- `ange-clashboard` → `:2048`
- `ange-panel` → `:3002`

`/opt`：`ange-clashboard` / `ange-panel` / `mihomo` / `containerd`  
`/docker`：仅残留旧 `mihomo` 目录（非当前挂载真源；真配置在 `/vol1/1000/docker/mihomo`）
