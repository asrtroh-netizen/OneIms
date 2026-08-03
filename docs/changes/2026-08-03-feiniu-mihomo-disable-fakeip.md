# 飞牛 Mihomo：关闭 Fake-IP（改 redir-host）

日期：2026-08-03  
主机：`/opt/mihomo/config/config.yaml`

## 原因

TUN + `enhanced-mode: fake-ip` 会把订阅域名解析成 `198.18.x`。  
订阅源又要求 **强制直连**，Fake-IP 直连极易 TLS 失败。用户确认：以前正常，关键是不要走 Fake-IP。

## 变更

```yaml
dns:
  enhanced-mode: redir-host   # 原 fake-ip
```

- 已备份：`config.yaml.bak.nofakeip.*`
- 执行：`docker compose up -d --force-recreate mihomo`（仅 restart 时曾残留假 IP 应答）
- 已调用：`POST /cache/fakeip/flush`

## 验证（本轮）

| 检查 | 结果 |
|---|---|
| 文件/容器内 `enhanced-mode` | `redir-host` |
| `getent hosts yyds.sbyun.org` | `202.155.141.21`（非 198.18） |
| `getent hosts em.mesl.cloud` 等 | 真实 IP，`fake=False` |
| Mihomo DNS API `yyds.sbyun.org` | 真实 A 记录 |
| Sub-Store `download/Cyber` | 仍 200（local 引导） |
| 直连拉取 Cyber 上游 | 仍可能 TLS 失败（线路/墙，与 Fake-IP 无关） |

## 注意

- `fake-ip-range` / `fake-ip-filter` 字段可留着，redir-host 下不生效。
- 订阅域规则继续保持 `🎯 全球直连`；provider `proxy: DIRECT`。
- 若以后再开 Fake-IP，订阅域必须进 filter 或改回 redir-host。
