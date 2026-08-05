# 删除远程仓库 OneIms-Lite（只保留 OneIms main）

## 动作

1. 删除分支 `credits-upstream`（仓内只留 `main`）
2. `gh repo delete asrtroh-netizen/OneIms-Lite --yes`

## 背景

- 该仓描述已标明 **MOVED →** `asrtroh-netizen/shizuku`（误命名的 Shizuku fork，IMS 在 OneIms）
- 删前：`stargazers_count=0`，`forks=0`
- 主产品仓 `asrtroh-netizen/OneIms` **保留**，且仅有 `main`

## 验证

```text
gh repo view asrtroh-netizen/OneIms-Lite  → Could not resolve Repository
gh repo view asrtroh-netizen/OneIms       → ok, defaultBranch=main
gh api .../OneIms/branches               → main only
```

## 说明

公开仓通用约定仍是「尽量不删仓保 Star」；本仓 0 Star 且已迁出，按用户当轮明确指令删除。
