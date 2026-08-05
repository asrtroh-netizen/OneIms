# 关闭公开源码面（README-only）

## 背景

公开仓描述与 README 已声明「不开放源代码 / 只提供 README + APK」，但 `main` tip 与多个远程分支仍含完整 `app/` 等源码。

## 已做

- 本地完整工程保留在分支 `oneims-private-full`（含当时 WIP）
- 公开 `origin/main` force-push 为 orphan：仅 `.gitignore` + `README.md` + `docs/screenshots/shizuku-asrtroh-home-active.png`
- 删除远程源码分支：`bloodline-3.0.9`、`docs/feiniu-ui-validate`、`feat/temp-root-comet-oneclick`、`feat/temproot-remote-so`、`release/v3.1`

## 残留风险

Release 关联的历史 tag 仍可能指向旧提交（浏览 tag 树仍可能看到源码）。若需彻底抹历史，需另做 tag/Release 迁移或重建仓库。

## 开发约定

日常开发只在 `oneims-private-full`（或后续私有远程）。**禁止**把私有分支 force-push / merge 回公开 `main`。
