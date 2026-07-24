# One 更新索引协议 · onetools.update.v1

## 商业定位（卖点边界）

| 层 | 是什么 | 是否你们的 IP | 卖产品时怎么说 |
|---|---|---|---|
| GitHub / GitLab / F-Droid **适配器** | 自研 Kotlin 调公开 HTTP API | **实现代码是你们的**；协议属于对方平台 | 「兼容主流源」，不是转售 Obtainium/F-Droid 客户端 |
| **One Index** | 自有 JSON schema + 客户端 | **契约与实现都是你们的** | 可做付费目录、私有 CDN、会员源 |
| Obtainium 源码 | 未引入 | N/A | 禁止整包合并（GPL 传染风险） |

结论：GitLab/F-Droid **可以且已经自研**（干净室适配器）。要「自我的、适合卖」的差异化，主推 **One Index**，公开源只做互通。

## Schema

见 `onetools/src/main/assets/sample-one-update.json`。

必填：`schema=onetools.update.v1`，`apps[]` 含 `id`、`apkUrl`；建议填 `packageName`、`versionName`、`changelog`、`sha256`。

## 客户端用法

- 添加源选 **One**，索引 URL 填 `https://cdn.your.domain/one-update.json`
- 或输入 `one:https://cdn.your.domain/one-update.json`
- 应用 id 对应 `apps[].id`

## 后续可售增强（未本轮）

- 索引签名（Ed25519）防篡改
- 会员 Token 拉取私有索引
- 后台定时检查 + 推送
