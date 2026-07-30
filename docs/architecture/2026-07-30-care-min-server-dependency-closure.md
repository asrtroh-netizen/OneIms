> ⚠️ **已废止现行方案（2026-07-30）**：OneKuku 迷你版 / CARE_MIN 已清除。见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · Care MINI server 最小依赖闭包（P3a 续）

**来源**：MCP 子代理只读侦察（主 Agent 复核采纳）  
**推荐接入**：vendor 源码进 `:care-min`（不 include 邻仓整仓；AGP/Java 版本不合）

## 必需模块

`server`（去 Plus）→ `server-shared` → `common` → `aidl` → `shared` → `starter` → `rish`（含 native）

客户端继续 Maven `dev.rikka.shizuku:api/provider:13.1.5`。

## 宿主化

| 点 | 目标 |
|---|---|
| Manager id | `com.oneims.app` |
| 进程名 | `onekuku_server` |
| Provider | `com.oneims.app.shizuku` |

## OUT

Manager UI、*PlusImpl、AICore、automation、强制装 `com.onekuku.care`。

详见白名单：`2026-07-30-care-min-server-import-whitelist.md`。

