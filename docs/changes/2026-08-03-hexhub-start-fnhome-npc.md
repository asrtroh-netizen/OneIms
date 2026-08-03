# 2026-08-03 · HexHub/飞牛上启动 FNHOME 的 npc（逐步）

目标：让 NPS 客户端「狗窝」从 **离线 → 在线**，恢复 `hfs.itt.fan` / `:1818`。

前置：人在 **FNHOME 同一局域网**（你刚远程开的那台 PC），外网 NPS 网页点「开启」不够。

## 路径 A · 飞牛网页「应用中心」（优先）

1. 电脑连上家用 Wi‑Fi/有线（和飞牛同一网段）。
2. 浏览器打开飞牛本机地址（常见）：
   - `http://192.168.2.2/`
   - 或路由器后台里看 HaloXFN / 飞牛的局域网 IP
3. 用飞牛账号登录（FNHOME 常见用户 `Halo`）。
4. 打开 **应用中心**（或「已安装应用」）。
5. 找到应用名 **NPC客户端**（`appname=npc`，版本多为 `0.26.32`）。
6. 若显示已停止/未运行 → 点 **启动**（或「打开」后再在应用内点启动）。
7. 进入 NPC 客户端配置页，确认大致为：
   - 服务器：`8.137.155.86:6666`（或你 NPS 实际地址）
   - 验证密钥：与 NPS 里「狗窝」那一行的 **唯一验证密钥**一致
8. 保存后等几秒，回到 **阿里云 NPS 客户端列表** 刷新：
   - 「狗窝」连接应变为 **在线**（不再灰「离线」）

## 路径 B · HexHub 终端一条命令（UI 找不到时）

1. 打开 **HexHub**，选中已保存的 FNHOME / HaloXFN 主机（局域网直连，不要走挂掉的 `hfs:1818`）。
2. 打开终端，执行：

```bash
/var/apps/npc/cmd/main start
```

若 `main` 不支持子命令，直接：

```bash
/var/apps/npc/target/npc -config /var/apps/npc/var/config/npc.conf
```

（飞牛官方启动脚本里的 `CMD` 就是上面这条。）

3. 再执行检查：

```bash
pgrep -a npc
# 期望看到：.../npc -config /var/apps/npc/var/config/npc.conf
```

4. 回 NPS 面板刷新，「狗窝」应变在线。

## 成功判据（三选一即可交叉确认）

| 检查 | 期望 |
|---|---|
| NPS 客户端「狗窝」 | 连接 = **在线** |
| 本机浏览器 | `http://hfs.itt.fan/` 不再长期 **502** |
| 外网 SSH | `hfs.itt.fan:1818` 能出 SSH banner / 能登录 |

## 仍离线时快速排查

1. 确认 `npc.conf` 里 `server_addr` 指向 `8.137.155.86:6666`，`vkey` 与「狗窝」一致。  
2. 看日志：`/vol1/@appdata/npc/npc.log`（或 `/var/apps/npc/var/npc.log`）。  
3. 本机能否访问 NPS：`ping 8.137.155.86` / 出网是否被代理/防火墙拦。  
4. 应用中心把 NPC 停掉再启动一次；仍不行可重启飞牛后再启 npc。

完成后回面板说「npc在线了」，即可继续装 Tailscale。
