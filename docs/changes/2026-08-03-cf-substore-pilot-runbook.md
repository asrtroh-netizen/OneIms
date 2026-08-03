# Cloudflare Sub-Store 免费试点 Runbook（仅 Cyber）

日期：2026-08-03  
目标：用免费 Workers 拉 Cyber，飞牛 Mihomo 直连消费；验证通了再扩 TAG 等。

## 架构

```text
Cyber 机场源 ──CF 出站──► Cloudflare Sub-Store (Workers+D1)
                                │
                                ▼ /download/...?token=DOWNLOAD
                       飞牛 Mihomo Cyber provider (proxy: DIRECT)
```

飞牛本地 Sub-Store / Cyber local 灌入先保留作备份，试点成功后再考虑切换。

## 你需要准备

1. Cloudflare 账号（免费即可）  
2. GitHub 账号（Deploy 按钮会 fork/导入仓库）  
3. 约 10 分钟

## 步骤 A · 生成 Token

本机已可生成（或你自己用密码器再来一对）：

```bash
# PowerShell / Python
python -c "import secrets; print(secrets.token_urlsafe(32)); print(secrets.token_urlsafe(32))"
```

- 第 1 行 → `SUB_STORE_ADMIN_TOKEN`（管理后台）  
- 第 2 行 → `SUB_STORE_PUBLIC_DOWNLOAD_TOKEN`（订阅下载）  
**两个必须不同，且不要发到公开群。**

## 步骤 B · 一键部署

打开（官方 Deploy 按钮同源）：

https://deploy.workers.cloudflare.com/?url=https://github.com/realchendahuang/sub-store-cloudflare

1. 登录 Cloudflare  
2. 按提示连接 GitHub 并创建 Worker + D1  
3. Secrets 填入上面两个 Token  
4. 等待 build/deploy 成功  
5. 记下 Worker 地址，形如：`https://xxx.<你的账号>.workers.dev`

## 步骤 C · 打开管理页并导入 Cyber

浏览器打开：

```text
https://<你的-worker>/?token=<ADMIN_TOKEN>
```

添加 Source（远程）：

| 字段 | 值 |
|---|---|
| 名称 | Cyber |
| URL | `https://yyds.sbyun.org/sby/1617dfc9912f364552db39d7dad18057` |

确认默认 Collection（如 Daily），复制 **Mihomo / Clash Meta** 下载链接。  
一般形态类似：

```text
https://<worker>/download/<collection-or-sub>?target=ClashMeta&token=<DOWNLOAD_TOKEN>
```

（以页面「复制链接」为准。）

## 步骤 D · 本机先验

在 Windows 上：

```bash
curl -I "粘贴的下载链接"
curl -o cyber-cf.yaml "粘贴的下载链接"
# 应 HTTP 200，文件里有 proxies:
```

再在飞牛上（或让我跑）：

```bash
curl -sS -o /dev/null -w '%{http_code}/%{size_download}\n' '同一链接'
```

## 步骤 E · 飞牛对接（你把链接发我后我改）

改 `/opt/mihomo/config/config.yaml` 中 `proxy-providers.Cyber`：

```yaml
  Cyber:
    type: http
    url: <CF下载链接>
    path: ./providers/Cyber.yaml
    interval: 10800
    proxy: DIRECT
    health-check:
      enable: true
      interval: 600
      url: http://www.gstatic.com/generate_204
```

然后 `PUT /configs?force=true` 或 recreate，检查 `Cyber` provider count > 0。

订阅域直连规则保持；CF 的 `*.workers.dev` 一般走直连即可（必要时加 `DOMAIN-SUFFIX,workers.dev,🎯 全球直连`）。

## 回传给我的最小信息（可打码中间几位）

```text
Worker基址: https://xxxx.workers.dev
管理是否OK: 是/否
Cyber下载链接: https://...  (含 target=ClashMeta)
下载实测: HTTP码 / 大小
```

**不要把 ADMIN_TOKEN 完整贴到公开处**；DOWNLOAD 链接整段发给我用于改配置即可。

## 失败时

| 现象 | 处理 |
|---|---|
| Deploy 卡在 GitHub 授权 | 换浏览器/确认 CF 绑定 GitHub |
| 管理页 401 | token 查询参数是否 ADMIN |
| 添加 Cyber 后拉取失败 | 机场拦 CF ASN → 试点失败，改年付美区鸡 |
| 飞牛拉 CF 超时 | 查是否仍 Fake-IP；workers.dev 是否被污染 |

## 成功标准

- CF 下载链接 HTTP 200 且含节点  
- 飞牛 `providers/proxies` 里 Cyber count > 0  
- 面板可选到 Cyber 节点（延迟另测）
