# 关闭公开源码面（README-only）

## 背景

公开仓描述与 README 已声明「不开放源代码 / 只提供 README + APK」，但 `main` tip 与多个远程分支仍含完整 `app/` 等源码。

## 为何页面又变了（2026-08-05 复盘）

本地开发分支 `oneims-private-full` 曾跟踪 `origin/main`，自动/手动 `git push origin HEAD:main` 把**完整源码树**盖回公开 tip。  
GitHub 页面因此从「README + APK」变成又出现 `app/`、`tools/` 等目录——不是手机 App UI 自己变了。

## 已做（含本次恢复）

- 本地完整工程保留在分支 `oneims-private-full`（**已取消**对 `origin/main` 的 upstream，避免再误推）
- 公开 `origin/main` 恢复为 README-only tip：仅 `.gitignore` + `README.md` + `docs/screenshots/shizuku-asrtroh-home-active.png`
  - 本次因本机 `git push` 443 不通，改用 GitHub Git Data API 重建 orphan tip：`8b3786049929cc46dc0ed345ecba42eb37182274`
- 仓库设为 **public**，描述改为 README + APK only / 不开源
- 远程源码分支此前已清理；当前仅见 `main`

## 残留风险

Release / 历史 tag 仍可能指向旧提交（浏览 tag 树仍可能看到源码）。若需彻底抹历史，需另做 tag/Release 迁移或重建仓库。

## 开发约定

日常开发只在 `oneims-private-full`（或后续**私有**远程）。**禁止**把私有分支 force-push / merge 回公开 `main`。  
公开 `main` 只允许更新 README / 截图 / Release 资产说明。
