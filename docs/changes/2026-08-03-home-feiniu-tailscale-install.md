# 2026-08-03 · 家用两台飞牛 Tailscale 安装进度

## 背景

办公室飞牛 `office-feiniu-ddos` 已在线（`100.82.92.117`）。本轮继续给家用 **TTFN**、**FNHOME/HaloXFN** 装 Tailscale，与 NPS 双轨并存。

## 结果

| 节点 | SSH | Tailscale | 主机名参数 | 状态 |
|---|---|---|---|---|
| TTFN | `tfs.itt.fan:4848` 通 | **1.98.10** / `tailscaled` active | `home-feiniu-ttfn` | **NeedsLogin** |
| FNHOME | `hfs.itt.fan:1818` TCP 通但 **无 SSH banner**（对端复位） | 未装上 | `home-feiniu-haloxfn` | **阻塞：NPS/npc 隧道异常** |

旁证：`http://hfs.itt.fan/` 返回 **502**（nginx 反代后端不可达），与 SSH banner 空读一致，更像 **FNHOME 侧 npc 客户端离线或隧道挂死**，不是本机脚本逻辑错误。

## TTFN 登录

机器上仍提示：

`https://login.tailscale.com/a/25286bb01f905`

同账号（`asrtroh@`）浏览器授权后应出现 `100.x`。若链接过期，在 TTFN 上执行：

```bash
sudo tailscale up --hostname=home-feiniu-ttfn --accept-dns=false
```

## FNHOME 恢复后手工安装

隧道恢复（SSH 能进 `HaloXFN`）后，可在机上执行仓库临时脚本思路（或官方一键）：

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo systemctl enable --now tailscaled
sudo tailscale up --hostname=home-feiniu-haloxfn --accept-dns=false
```

本机侧探测命令（应看到 `SSH-2.0-...` banner，而不是空字节）：

```text
TCP hfs.itt.fan:1818 → recv SSH banner
```

## 验证摘要（本轮）

- TTFN：`tailscale version` → 1.98.10；`systemctl is-active tailscaled` → active；`tailscale status` → Logged out + 上述 URL
- FNHOME：多次间隔重试（paramiko / OpenSSH）均 `Error reading SSH protocol banner` / `Connection closed by remote host`；HTTP `hfs.itt.fan` → 502

## 未做

- 未拆除 NPS（按既定双轨策略）
- FNHOME 未取得 auth URL / `100.x`
- 手机端 Tailscale / 私有域名绑定 `100.x` 仍待后续

## 2026-08-03 续 · FNHOME 开机后 npc 仍掉线

用户反馈：FNHOME 机子已好、远程开机了 PC，但 **npc 掉了**。

本轮外网复测（开机后）：

- `http://hfs.itt.fan/` → **仍 502**
- `hfs.itt.fan:1818` → TCP 通，**SSH banner 空 / 对端复位**（约 20 次轮询仍 `STILL_DOWN`）

对照 TTFN 正常 npc：

```text
/var/apps/npc/target/npc -config /var/apps/npc/var/config/npc.conf
```

**结论**：外网入口依赖 FNHOME 侧 npc；npc 未起时远程无法 SSH，也就无法代启 npc / 代装 Tailscale（鸡生蛋）。需在 **同局域网 PC → HexHub → 应用 npc → 启动**（或本机执行上述命令）后，再继续安装 `home-feiniu-haloxfn`。

### NPS 面板「开启」仍「离线」含义（截图核对）

客户端备注 **狗窝**（ID 4，版本 0.26.32）：

| 面板字段 | 含义 |
|---|---|
| 绿色 **开启/开放** | 服务端配置已启用，允许该客户端接入 |
| 灰色 **离线** | **客户端进程当前没有连上** NPS（`8.137.155.86:6666`） |
| 客户端地址 `113.x` | 多为上次在线时的公网出口，离线时不代表此刻已连通 |

对照在线侧 TTFN：进程 `npc -config .../npc.conf`，且有到 `8.137.155.86:6666` 的 `ESTAB` 连接。FNHOME 需要同样把飞牛应用 **npc** 跑起来（不只是 NPS 网页点开启）。
