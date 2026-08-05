# 删除远程 GitHub 仓库（本地保留）

> ⚠️ **本路线已废止（2026-08-05）**  
> 删仓导致 Star 清零且不可恢复。现行硬规矩：**禁止删仓**，见 [`docs/guides/github-public-repo-conventions.md`](../guides/github-public-repo-conventions.md)。下文仅作事故档案。

## 背景

用户明确：撤回「公开 README-only」做法；目标是 **删除远程仓库**，本地完整工程保留。

## 已做

- `gh repo delete asrtroh-netizen/OneIms --yes`（验证 `gh repo view` 无法解析）
- 本地继续在 `oneims-private-full`，源码与 README 均在
- 远程 `origin` 已移除；仅保留失效备注远程名 `origin-deleted-oneims`（指向已删除地址）

## Release APK

删除前尝试下载备份，因网络过慢未完整完成（NOT RUN 全量）。本地可用源码重新构建 APK。
