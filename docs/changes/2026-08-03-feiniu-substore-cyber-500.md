# 飞牛 Sub-Store Cyber 下载 500 排查与临时修复

日期：2026-08-03  
主机：TTFN 飞牛 NAS（RK3568，`192.168.2.2`）

## 现象

访问 `http://sub.itt.fan/download/Cyber`（及本机 `127.0.0.1:8965/download/Cyber`）返回 **HTTP 500**，正文类似：

`订阅 Cyber 中不含有效节点`

用户侧反馈：本机/外网「Cyber 可以直接拉」。

## 根因（证据链）

1. Sub-Store 日志真实失败是 **上游 TLS 下载失败**，不是节点解析本身：
   - URL：`https://yyds.sbyun.org/sby/...`
   - `Client network socket disconnected before secure TLS connection was established`
   - `Proxy: undefined`（Sub-Store **未配置出站代理**）
2. 飞牛开启 Mihomo TUN + `dns-hijack: any:53` + `fake-ip` 后，主机/容器解析订阅域名为 `198.18.x`；流量进 TUN 后原先命中 **`MATCH → 🐟 漏网之鱼 → 🎯 全球直连`**，直连海外订阅域失败。
3. 对照验证：
   - **Windows 本机直连**同一 Cyber URL：`HTTP 200`，约 61KB（源站存活）。
   - 飞牛经 OpenClash `192.168.2.5:7890` / 本机 Mihomo 节点：对 Cyber 上游仍失败。
   - 飞牛当时可用落地节点（自建 VLESS / 链式 SOCKS / 香港家宽）delay **503**；Cyber 灌入后的台湾 TUIC 节点 dial 亦超时——**飞牛侧稳定出站尚未恢复**。
4. 因此「本机可拉、飞牛 Sub-Store 500」并不矛盾：源站正常，**飞牛拉源路径不通**；Sub-Store 把空结果包装成「不含有效节点」。

## 已做修复

| 项 | 状态 |
|---|---|
| 本机下载 Cyber YAML，注入 Sub-Store `Cyber.source=local` + `content` | 已完成 |
| `http://127.0.0.1:8965/download/Cyber` / `?target=ClashMeta` | **200**（约 31–33KB） |
| Mihomo `proxy-providers.Cyber` 改为 `type: file` → `./providers/Cyber.yaml` | 已完成 |
| Provider 加载 | **Cyber count=99**；`🇨🇳 台湾节点` 已出现 Cyber 台湾组 |
| 配置中为订阅域增加走代理规则（`sbyun.org` 等 → `🚀 节点选择`） | 已写入；出站仍受节点可用性限制 |
| `🐟 漏网之鱼` | 已恢复为 `🎯 全球直连` |

备份：

- `/opt/sub-store/data/sub-store.json.bak.*`
- `/opt/mihomo/config/config.yaml.bak.*`
- 引导文件：`/opt/sub-store/data/bootstrap/cyber.yaml`

## 未恢复 / 风险

- Sub-Store **Cyber 现为 local 源**，不会自动跟随机场更新；要恢复 remote 需飞牛具备可用出站后再改回 `source=remote`。
- TAG / MESL / Guigui / Dabei / DBB / Emby 的 HTTP provider 仍为 **0 节点**（拉 Sub-Store 仍可能 500）。
- Cyber 台湾 TUIC 在飞牛上 delay/dial 未通过；**不能**仅凭 provider 计数声称「代理已可用」。
- 勿把 `sub.itt.fan` 钉到 `192.168.2.2`（会撞系统 nginx `download_auth` → 403）；公网解析应保持 NPC（`8.137.155.86`）。

## 验证命令（本轮）

```bash
curl -sS -o /dev/null -w '%{http_code}/%{size_download}\n' \
  'http://127.0.0.1:8965/download/Cyber?target=ClashMeta'
# 期望：200/31487 量级

curl -sS -H "Authorization: Bearer $(cat /opt/mihomo/API_SECRET)" \
  http://127.0.0.1:9090/providers/proxies | python3 -c \
  'import sys,json;d=json.load(sys.stdin)["providers"];print("Cyber",len(d["Cyber"]["proxies"]))'
# 期望：Cyber 99
```

Windows 对照（源站）：

```text
curl Cyber URL → 200 / ~61021
```

## 建议下一步

1. 修复飞牛出站（自建 VLESS / 链式入口，或先让 TAG 可用），再把 Cyber 改回 remote 并测自动更新。
2. 同样方式引导 TAG 等订阅，或等出站恢复后由 Sub-Store 自行拉取。
3. 面板将 `🚀 节点选择` 切到实际可用节点后，再验收 Google / YouTube / TV 网关。
