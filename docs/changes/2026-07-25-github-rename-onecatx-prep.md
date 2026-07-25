# 2026-07-25 · GitHub 用户名改名准备：asrtroh-netizen → OneCatx

## 你只需确认的一步（账号级）

打开并改名：

https://github.com/settings/admin

目标用户名：**OneCatx**（与 `onecatx` 同一名字，大小写不敏感）

改名前 API 核验：`users/OneCatx` → 404（未见占用）。最终以 Settings 提交结果为准。

## 本仓已预改（活链接 / 运行时常量）

| 位置 | 用途 |
|---|---|
| `UpdateChecker.REPO_OWNER` | App 内检查更新 |
| `OneKukuCoreComponent.CORE_REPO_OWNER` | OneKuku 相关仓库常量 |
| `SettingsScreen` Shizuku GitHub 链接 | 设置页外链 |
| `README.md` | 对外下载 / Release / Shizuku 链接 |
| `onetools` 单测示例 owner | 解析测试 |
| `docs/product/...-blocklist-cdn-publish.md` | OneBlock raw/release URL |

历史 `docs/changes/*` 归档记录**保留旧名**（当时事实）。

## 改名成功后还要做

1. 本地：`git remote set-url origin https://github.com/OneCatx/OneIms.git`
2. 其它克隆仓（shizuku / OneBlock / oneboard…）同步改 remote
3. Telegram / CDN / 第三方文档里写死的旧链接人工替换
4. 回复面板「已改名」，再核验 `gh api user` 的 login

## 不会自动做

- **不会**用 API 静默改 GitHub 用户名（必须你网页确认）
- **不会**擅自 `git push`（你已禁止）
