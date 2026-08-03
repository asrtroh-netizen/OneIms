# Sub-Store 部署到 Cloudflare：可行性评估

日期：2026-08-03  
背景：飞牛本机拉机场源易失败；用户询问 CF 部署是否现实。

## 结论

**现实，而且对你现在的痛点很对症。**  
不是幻想架构：社区已有可一键部署的 Cloudflare Workers + D1 方案（如 `sub-store-cloudflare`），官方 Sub-Store 生态也长期支持边缘/Workers 形态。

适合解决的问题：

- 订阅源拉取放在 **Cloudflare 边缘出口**，不依赖飞牛直连/TUN
- Mihomo `proxy-providers` 只消费「已生成好的下载 URL」（对 CF 域名可直连）
- 与「订阅源强制直连」不冲突：直连的是 CF，不是机场源站

## 推荐架构（飞牛不拆掉）

```text
机场源 URL ──(CF 出站拉取)──► Cloudflare Sub-Store (Workers+D1)
                                      │
                                      ▼ /download/xxx?token=...
                         飞牛 Mihomo proxy-providers (proxy: DIRECT)
                                      │
                                      ▼
                              局域网 TUN / 7890 / TV 网关
```

可选保留：

- 飞牛 `Sub2Store`：仅作备份或内网面板
- 现有 NPC `sub.itt.fan`：要么继续指飞牛，要么以后 CNAME 到 Worker 自定义域

## 怎么部署（概览）

1. Cloudflare 账号 + 免费 Workers/D1 足够起步  
2. 一键 Deploy to Cloudflare，或 Wrangler CLI  
3. 配置：
   - `SUB_STORE_ADMIN_TOKEN`（管理）
   - `SUB_DOWNLOAD`/`PUBLIC_DOWNLOAD` token（订阅链接）
4. 在 CF 面板导入 Cyber/TAG/… 源  
5. 飞牛 Mihomo provider URL 改为 CF 下载地址，`proxy: DIRECT`  
6. 验证：provider count > 0，面板延迟正常

## 风险与边界

| 风险 | 说明 |
|---|---|
| 机场反机房 IP | 少数源会拦 CF ASN；不通就换源或加 CF 侧代理（若方案支持） |
| Workers 配额 | 免费版 CPU/子请求有上限；家庭用量通常够，别拿去给公网白嫖 |
| 数据迁移 | 与飞牛 Docker Sub-Store 不是自动同步，需导出/重配 |
| 功能差异 | CF 兼容版 ≠ Docker 全功能 1:1；以实际下载 ClashMeta 为准 |
| DNS | `sub.itt.fan` 若切到 CF，要改解析；切之前先用 `*.workers.dev` 验证 |

## 和不做 CF 的对比

| 方案 | 优点 | 缺点 |
|---|---|---|
| 只修飞牛出站/直连 | 架构简单 | 受家宽/墙影响大 |
| 飞牛 Sub-Store local 灌入 | 立刻可用 | 不自动更新 |
| **CF Sub-Store** | 拉源稳定、自动更新、外网也能用 | 多一层账号与迁移 |

## 建议决策

1. **先做**：用 Workers 一键部署空实例，只导入 Cyber，飞牛 provider 试一条  
2. **验证通过再**：迁 TAG 等，考虑是否把 `sub.itt.fan` 切过去  
3. **暂不做**：全量替换飞牛面板/一次性迁全部订阅

本评估不下线飞牛现网；实施需你点头后再动手。
