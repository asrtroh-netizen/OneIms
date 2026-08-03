# 2026-08-03 · TTFN Sub-Store 域名怎么绑

## 结论（本轮实测）

TTFN 上 Sub-Store **已经绑好域名**，公网可用：

| 检查 | 结果 |
|---|---|
| 进程 | `node .../sub-store.bundle.js` 在跑 |
| 本机端口 | `0.0.0.0:8965` LISTEN |
| 本机首页 | `http://127.0.0.1:8965/` → **200** |
| 本机下载 | `http://127.0.0.1:8965/download/Cyber?target=ClashMeta` → **200** / ~31KB |
| DNS | `sub.itt.fan` → **`8.137.155.86`**（阿里云 NPS 机） |
| 公网首页 | `http://sub.itt.fan/` → **200**（Sub-Store UI 2.29.4） |

## 绑定原理（三截火车）

```text
浏览器
  → DNS: sub.itt.fan = 8.137.155.86
  → 阿里云 NPS「主机/HTTP」反代（按 Host 选隧道）
  → TTFN 侧 npc 把流量转到 127.0.0.1:8965
  → Sub-Store
```

**不要**把 `sub.itt.fan` 的 A 记录直接指到家里局域网 `192.168.2.2`：会撞飞牛系统 nginx（`download_auth` → 403）。必须指公网 NPS IP。

## 若要新建/重绑一个域名（通用步骤）

1. **DNS**（域名服务商）：`新域名` A 记录 → `8.137.155.86`（与 `tfs/hfs/sub.itt.fan` 同落点）
2. **NPS 服务端**（你截过的管理页）→ 对应客户端（TTFN 那条，不是离线的「狗窝」）→ **主机**  
   - 域名：`新域名`  
   - 目标：`127.0.0.1:8965`（或 `192.168.2.2:8965`，以 npc 所在机为准用 loopback 更稳）  
   - 方案/HTTPS 按你现网习惯（现 `sub.itt.fan` 先走 HTTP 也通）
3. **确认 TTFN Sub-Store 在听 8965**（本轮已确认）
4. **验证**：
   ```bash
   curl -I http://新域名/
   curl -sS -o /dev/null -w '%{http_code}/%{size_download}\n' \
     'http://新域名/download/Cyber?target=ClashMeta'
   ```

## 常用地址

- 面板：`http://sub.itt.fan/`
- 局域网直连：`http://192.168.2.2:8965/`（仅家用网）
- 下载示例：`http://sub.itt.fan/download/Cyber?target=ClashMeta`

## 注意

- 公网务必带 Sub-Store **token**（若已开启），避免面板/下载裸奔。
- TTFN 的 npc 必须在线；npc 挂了域名会 502（与 FNHOME 狗窝同类问题）。
- Cloudflare Workers 试点是另一条线；未切过去前，继续用 NPC 这条即可。
