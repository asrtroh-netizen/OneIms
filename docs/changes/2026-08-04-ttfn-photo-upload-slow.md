# 2026-08-04 · TTFN 飞牛上传照片慢

节点：TTFN（媳妇家飞牛，`192.168.2.2` / Tailscale `100.64.118.44` / SSH `tfs.itt.fan:4848`）

## 结论

**不是磁盘/CPU 坏了，是上传走了公网 NPS 反代，带宽被掐。**

本机健康，远程经 `tn.itt.fan` / `fn.itt.fan`（DNS→`8.137.155.86`→NPS→npc）上传慢属路径问题。

## 证据

| 检查 | 结果 |
|---|---|
| load / idle | load ≈0.5，CPU idle ≈92% |
| 本机 `dd` 直写 | **~585–601 MB/s** |
| SFTP 上传 8MB via NPS `tfs.itt.fan:4848` | **≈18.2 Mbps** |
| SFTP 上传 8MB via Tailscale `100.64.118.44:22` | **≈32.6 Mbps** |
| DNS `tn.itt.fan` / `fn.itt.fan` | 均 `8.137.155.86` |
| VPS nginx | `tn`→`:9527`、`fn`→`:9999`（NPS 监听） |
| TTFN `:9999` | 飞牛 Web **200**；已装 `lite.gallery`，`imagesrv`/`mediasrv` 在跑 |

## 链路

```text
手机/电脑 ──DNS──► tn|fn.itt.fan = 8.137.155.86
                     │
                     ▼
              阿里云 nginx → NPS TCP
                     │
                     ▼
              TTFN npc → 本机 :9999 / 相册
```

即便人身在媳妇家局域网，若 App/浏览器仍用公网域名，流量也会 **出网绕 VPS 再回来（NAT 回环）**，体感同样慢。

## 建议用法

| 场景 | 应用法 |
|---|---|
| 媳妇家 Wi‑Fi | `http://192.168.2.2:9999`（或飞牛局域网发现），**不要**用 `tn/fn.itt.fan` 传大图 |
| 外网远程 | Tailscale `http://100.64.118.44:9999` 或 MagicDNS `home-feiniu-ttfn` |
| 公网域名 | 仅应急浏览；批量传照片不适合 |

可选增强（未做，需授权）：家里路由/mihomo 做 split DNS，把 `tn.itt.fan`/`fn.itt.fan` 在局域网解析到 `192.168.2.2`。

## 2026-08-04 补 · 用户确认「在外面」

外网场景成立。复测：

| 检查 | 结果 |
|---|---|
| `http://100.64.118.44:9999/` | **200**（Tailscale，当前 direct） |
| `http://tn.itt.fan/` / `fn.itt.fan/` | **200**（首页小文件快，不代表上传吞吐） |
| Tailscale 节点 `home-feiniu-ttfn` | `active; direct` |

外网传图优先级：**手机/电脑开同一 Tailnet → 用 `100.64.118.44:9999`（或 MagicDNS）**；公网域名仅应急。

## 2026-08-04 补 ·「阿里云不是 200M 吗」

用户指出 VPS 套餐约 **200M 峰值**。分段复测：

| 段 | 结果 |
|---|---|
| 办公室 PC → NPS → TTFN 上传 8MB | **≈19 Mbps** |
| 办公室 PC ← NPS ← TTFN 下载 8MB | **≈95 Mbps** |
| **VPS 本机** `127.0.0.1:4848` → TTFN 上传 16MB（排除客户端上行） | **≈95.3 Mbps** |

结论：

1. **套餐 200M ≠ 手机传图 200M**：端到端取最慢一跳。  
2. VPS+NPS+TTFN 家宽接收能力约 **~95Mbps**（已接近百兆家宽量级；峰值 200M 还受单连接/共享带宽影响）。  
3. 此前「只有十几 Mbps」主要是 **你所在网络的上行**（办公室/手机上行）在卡，不是阿里云没开 200M、也不是飞牛磁盘慢。

## 回滚

N/A（本轮只读采证，未改配置）。
