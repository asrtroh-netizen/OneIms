# 2026-07-30 · 废止本地产品号 3.0.9

## 决策

用户连续拍板：

1. **OneIMS 3.0.4 作为底包**（架构真源：`docs/architecture/2026-07-30-oneims-3.0.4-base-package.md`）
2. **彻底删除 3.0.9**（废止本地升号身份，不再用 `3.0.9` / `versionCode 79` 命名当前构建）

## 改动

| 项 | 前 | 后 |
|---|---|---|
| `app/build.gradle.kts` `oneImsVersionName` | `3.0.9` | `3.0.4` |
| `versionCode` | `79` | `74` |
| README What's New · 3.0.9 | 有 | **删除**；改为「本地底包身份」说明 |
| `docs/changes/2026-07-30-bump-3.0.9-local-no-release.md` | 升号决策 | **作废**（文首标注） |

## 明确不做

- 不 `git reset --hard` 到 `3146d2538`（不抹掉后续代码演进）
- 不删除已发版 GitHub `v3.0.8` 资产
- 不删除历史 changelog 中对 3.0.9 的考古叙述（仅去掉「当前身份」）

## 安装注意

从已装 `versionCode ≥ 75`（含公开发版 3.0.8=`78`、曾装本地 3.0.9=`79`）降到底包 `74` 时，系统拒绝覆盖安装 → **先卸载再装**。

## 验证

```text
Select-String app/build.gradle.kts -Pattern "oneImsVersionName|versionCode"
→ 3.0.4 / 74
rg -n "oneImsVersionName|versionCode = " app/build.gradle.kts
```
