# OneIms 公开仓分支扫描（只留 main）

## 结果

| 检查项 | 结果 |
|---|---|
| `refs/heads/*` | **仅 `main`** |
| 受保护分支 | 无额外分支 |
| Open PR | 0 |
| tags（列表头） | 空 |
| default branch | `main` |
| `main` tip 顶层 | `.gitignore` / `README.md` / `docs`（README-only 面，无 `app/`） |

**无需删除任何远程分支。**

## 命令证据

```text
gh api repos/asrtroh-netizen/OneIms/branches --jq '.[].name'  → main
gh pr list --repo asrtroh-netizen/OneIms --state open         → []
gh api repos/asrtroh-netizen/OneIms/contents?ref=main         → .gitignore README.md docs
```
