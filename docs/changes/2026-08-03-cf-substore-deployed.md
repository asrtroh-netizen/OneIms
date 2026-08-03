# Cloudflare Sub-Store 试点部署（2026-08-03）

## 结论

`feiniu-sub-store` Worker **已部署成功**，D1 **已迁移并完成 seed**（7 个机场源 + `daily` 集合）。  
飞牛本机对 `workers.dev` **直连 TLS 不可用**；需走 Mihomo `🚀 节点选择`。  
本轮结束时飞牛 Cyber 节点出现 `failed to create session: EOF`，因此 **下载链路运行时验收未闭环**（曾短暂观察到经 `127.0.0.1:7890` 访问 Worker UI 返回 200）。

## 已落地

| 项 | 值 |
|---|---|
| Worker | `https://feiniu-sub-store.asrtroh.workers.dev` |
| Account | `b20996ac8f1bca6c1d0cb9577b520c06` |
| D1 | `feiniu-sub-store` / `ec6b65b5-61cc-4ad8-a8da-e45c6686daa9` |
| Seed sources | cyber, tag, mesl, guigui, dabei, dbb, emby |
| Collection | `daily`（空 `sourceIds` = 全部启用源） |
| Deploy version | `8d97e1ae-25ad-4949-81c0-d076b322d9ef` |

本地工作副本：`e:\GQ\One\OneIMS\.tmp-cf-substore`（含 Windows 构建修补，不作为上游提交目标）。

### 安装过程中的修补

1. Windows 前端构建：`VITE_API_URL=/ pnpm ...` 在 cmd 下非法 → `scripts/build-frontend-win.mjs` + `package.json` `build:frontend`。
2. D1 seed：`BEGIN TRANSACTION` / `COMMIT` 被 remote D1 拒绝 → `scripts/render-seed-sql.mjs` 去掉事务包裹后导入成功（8 queries）。

### 飞牛侧路由

- `DOMAIN-SUFFIX,workers.dev,🚀 节点选择`（**不要**改成全球直连：直连 Cloudflare TLS 会被重置）。
- 机场源域名仍保持 `🎯 全球直连`（`sbyun.org` / `central-world.org` / `mesl.cloud` / `nimenshishangdi.cc` / `dbsur.top`）。
- 追加 hosts：`feiniu-sub-store.asrtroh.workers.dev: 104.16.124.96`（缓解污染；仍需可用代理出站）。

## 管理 / 下载 URL（token 见本地 `config/agent-setup.local.json`，勿提交仓库）

- Admin UI：`https://feiniu-sub-store.asrtroh.workers.dev/?token=<ADMIN>`
- Sources API：`/api/sources`（Bearer admin）
- 单源：`/download/source/cyber/mihomo?token=<DOWNLOAD>`
- 合集：`/download/collection/daily/mihomo?token=<DOWNLOAD>`

## 验证

| 检查 | 结果 |
|---|---|
| Wrangler deploy | PASS → workers.dev URL |
| D1 migrations | PASS（0001–0003） |
| D1 seed import | PASS（去掉 BEGIN/COMMIT 后） |
| Windows 直连 workers.dev | FAIL（DNS 污染 / SNI reset） |
| 飞牛直连 Cloudflare | FAIL（TLS EOF） |
| 飞牛经 7890 访问 Worker UI | 曾 PASS（200 HTML），随后因节点 `session EOF` 失败 |
| cyber/daily 下载内容校验 | **NOT RUN**（出站节点不健康） |
| 自定义域名 `substore.itb.one` | **BLOCKED**（当前 API Token 无 Zone DNS / Workers Routes 写权限） |

## 下一步

1. 面板把 `🚀 节点选择` 切到延迟正常的叶子节点后，再验：
   - `curl -x http://127.0.0.1:7890 '<download url>'`
2. 扩 Token 权限后绑定 `substore.itb.one`（避开 `workers.dev` 污染面）。
3. Mihomo `proxy-providers` 改指向 CF download URL，且 **`proxy:` 走可用代理组**（不能 `DIRECT`）。
4. 确认 CF 边缘能拉到各机场源后，再考虑收缩飞牛本地 Sub-Store bootstrap。
