# 2026-08-09 · FNHOME 同步 TTFN：AnGe host 网修内网 7890→:2048

## 背景

TTFN 家侧已用 **AnGe `network_mode: host`** 解决「经 mihomo `7890` 访问 `192.168.2.2:2048` → 502」（Docker 端口发夹；保留 TUN 网关）。用户要求同步到 **FNHOME / HaloXFN**。

## 复现（本轮）

| 检查 | 结果 |
|---|---|
| SSH `Halo@hfs.itt.fan:1818` | 通（hostname `HaloXFN`） |
| AnGe NetworkMode | `ange-clashboard_default`（bridge）+ `0.0.0.0:2048->2048` |
| 直连 `127.0.0.1:2048` / `192.168.2.2:2048` | **200** |
| `curl -x 127.0.0.1:7890 http://192.168.2.2:2048/` | **502** |
| 同代理 → `:9999` / gstatic | **200** / **204** |

## 已落地（最小刀）

只改 AnGe，**未**关 TUN、**未**动 AdGuard、**未**堆 mihomo LAN 规则（与 TTFN 复盘后的最小解对齐）。

1. 用临时 compose 重建：`network_mode: host`，数据卷仍挂 `/opt/ange-clashboard/data`
2. 持久化：`/opt/ange-clashboard/compose.yaml` 改为 host（备份 `compose.yaml.bak-bridge-*`）

## 验收

| 检查 | 结果 |
|---|---|
| NetworkMode | **host** / running |
| `proxy2048` | **200** |
| `gstatic` via 7890 | **204** |
| `ange-panel` `:3002` | **200**（未误伤） |

## 回滚

```bash
cp -a /opt/ange-clashboard/compose.yaml.bak-bridge-* /opt/ange-clashboard/compose.yaml  # 选对应备份
cd /opt/ange-clashboard && docker compose up -d
```

## 对照

- TTFN 同类：`docs/changes/2026-08-08-ttfn-office-proxy-bypass.md`（家侧内网节）
- FNHOME TUN 网关既有：`docs/changes/2026-08-03-fnhome-tun-gateway-sync.md`
