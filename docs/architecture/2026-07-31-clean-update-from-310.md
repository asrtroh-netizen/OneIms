# 从 3.1.0 干净更新（发布约定）

## 基线

| 项 | 值 |
|---|---|
| versionName | `3.1.0` |
| versionCode | `80` |
| 本地源码分支 | `main`（干净工作树，无 Broker WIP / 无 stash） |
| 公开 Release | `v3.1.0`（双 APK：OneKuku + Lite） |
| 公开 git | `origin/main` **仅 README**，禁止裸推源码 |

## 之后每次更新必须

1. **先确认干净**：`git status` 空、`git stash list` 空；有无关 WIP 先独立分支或丢弃，**禁止**带进发版包。
2. **在 3.1.0 血缘上改**：功能/UI/修复直接改 `main`，再按需升 `versionName` / `versionCode`。
3. **双包同号**：`:app:packageDualDebugApks` → `gh release`（新 tag 或同号 `--clobber`，按产品决定）。
4. **README-only 推公开仓**：worktree 拷贝 `README.md` → `origin/main`，勿 `git push origin main` 推源码。
5. **What’s New / Release notes 同步**，且与 APK 行为一致。

## 明确禁止

- 发版构建时工作区留 HyperOS Broker / 探针脚本 / `.tmp_*`
- 为躲脏树反复 stash 却不验收「干净树再打包」
- 源码仓与 README 公开仓历史混推
