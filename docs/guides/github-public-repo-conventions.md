# OneIms 公开 GitHub 仓 · 开发约定（硬规矩）

> 适用仓库：`https://github.com/asrtroh-netizen/OneIms`  
> 目标：公开面只提供 **README + Release APK**，本地保留完整源码；**绝不因仓操作丢掉 Star / Release / 讨论资产**。

## 绝对禁止

1. **禁止删除远程仓库**（`gh repo delete` / GitHub Settings → Delete this repository）  
   - 删仓会：**Star 清零、Watchers/Forks 断裂、旧 Issue/Discussion 丢失、Release 链接失效**  
   - 2026-08-05 已发生一次删仓重建 → Star 不可恢复（教训）
2. **禁止**把本地全量源码分支（如 `oneims-private-full`）`push` / `merge` / `force-push` 到公开 `origin/main`
3. **禁止**在公开 `main` tip 上出现 `app/`、`tools/`、`gradle*` 等源码树

## 允许的替代动作（想「清场」时用这些）

| 你想做的事 | 正确做法 |
|---|---|
| 隐藏源码面 | 保持 / 恢复 **README-only** tip（只含 README、截图、`.gitignore`） |
| 临时不公开浏览 | `gh repo edit --visibility private`（**不要删仓**） |
| 再公开 | `gh repo edit --visibility public`（星与 Release 仍在） |
| 更新说明 / 下载链 | 只更新公开 tip 的 `README.md`（及必要截图） |
| 发版 | `gh release` 上传 APK；README 改直链 |

## 本地开发

- 日常开发分支：`oneims-private-full`（或后续**私有**远程）
- **不要**给该分支设置 `upstream = origin/main`
- 公开 `main` 与私有全量分支物理分离；公开 tip 可用 orphan / Git Data API 维护

## 相关文档

- `docs/changes/2026-08-05-close-public-source-readme-only.md`
- `docs/changes/2026-07-16-dual-version-release-sop.md`
- `docs/changes/2026-08-05-delete-remote-keep-local.md`（反面教材：已废止「删仓」路线）
