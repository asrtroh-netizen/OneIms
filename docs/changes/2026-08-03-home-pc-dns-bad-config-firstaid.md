# 2026-08-03 · 家里 PC DNS_PROBE_FINISHED_BAD_CONFIG 急救

## 现象

Windows 打开百度失败：`DNS_PROBE_FINISHED_BAD_CONFIG`（找不到服务器 IP）。

## 最可能原因

此前 FNHOME 做过 **TUN 网关 + DNS :53**，设备用法是网关/DNS 填 `192.168.2.2`。  
若飞牛关机、mihomo/DNS 异常，或 PC 仍把 DNS 指到 `192.168.2.2`，就会出现本错误。

上级路由是小米 BE6500：`192.168.2.1`。

## 急救（在家里那台 Windows 上）

### 1）先改 DNS（最快）

管理员 PowerShell：

```powershell
Get-NetAdapter | Where-Object Status -eq 'Up' | Format-Table Name, InterfaceDescription, Status
# 把下面 "以太网" 换成你实际上网的网卡名（Wi-Fi / 以太网）
Set-DnsClientServerAddress -InterfaceAlias "以太网" -ServerAddresses ("192.168.2.1","223.5.5.5")
ipconfig /flushdns
nslookup www.baidu.com 192.168.2.1
```

图形界面：设置 → 网络 → 你的 Wi‑Fi/以太网 → DNS → 手动 → `192.168.2.1` 与 `223.5.5.5`。

### 2）关掉坏代理（若改 DNS 仍不行）

```powershell
netsh winhttp show proxy
# 若有代理且指向飞牛/127.0.0.1，先关系统代理：
Settings → 网络和 Internet → 代理 → 关闭「使用代理服务器」
```

浏览器扩展里的代理/SwitchyOmega 也先切直连。

### 3）验证

- 浏览器能开 `https://www.baidu.com`
- `nslookup www.baidu.com` 返回 IP

## 恢复上网后再做

1. 用局域网打开飞牛 `http://192.168.2.2/` → 启动 **NPC客户端**  
2. NPS「狗窝」变在线后，再考虑是否把 DNS 改回飞牛网关（可选；不稳就继续用路由 DNS）

## 远程侧

办公室外网仍无法进 FNHOME（npc 离线），本轮急救依赖你本机操作；远程代改 DNS **不可行**。
