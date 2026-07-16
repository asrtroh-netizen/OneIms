# 2026-07-16 · 最终改动与 Push 进度盘点

**类型**：只读进度（本文件为盘点记录）  
**规模**：S

## 总览（一眼）

| 面 | 状态 | 指针 |
|---|---|---|
| GitHub Release `v2.2.1` + 双 APK | ✅ 已推送 | assets: `…-standalone-…` / `…-Shizuku-…` |
| 公开仓 `origin/main` README | ⚠️ 已推但**直链文件名过期** | tip `de21754` 仍指向中文文件名 URL |
| 本地 README 直链修正 | ✅ 工作区已改对，**未推公开仓** | 与真实 assets 一致 |
| 双渠道源码（flavor 拆分等） | ❌ 未提交、未推送 | 约 37 已跟踪变更 + 44 未跟踪 |
| 本地 `main` 跟踪远端 | ❌ 无 upstream | 与 `origin/main` 历史分叉（SOP：公开仓仅 README） |

## 已 Push（公开侧）

1. **Release**：`v2.2.1`（Latest，2026-07-16T09:16Z）
   - `OneIms-OneKuku-standalone-2.2.1.apk`（~41.9MB）
   - `OneIms-OneLink-Shizuku-2.2.1.apk`（~26.4MB）
2. **origin/main**：`de21754` — README v2.2.1 选购/升级说明（仅 README 线）

## 未 Push / 未闭环

1. **P0**：公开 README 下载链仍是中文 URL 编码名，与真实 assets **不一致** → 用户点 README 可能 404；本地 README 已改成 ASCII 名，需经 worktree SOP 再推一刀。
2. **双渠道源码工作树**：未 commit（含 `app/src/onekuku/`、`app/src/onelink/`、Manifest/Privilege/Home/Sleep、今日 docs/scripts 等）。
3. **本地提交**：`450b6e2` 中断复盘文档等在本地 `main`，不在公开 README 线。

## 本地最终改动归属（未提交主线）

- 双 flavor 脚手架与 OneLink 精简 / OneKuku 内嵌栈下沉
- 版本号工作区：`2.2.1` / `versionCode 66`
- 行为与 UI 策略（Home/Boot/Sleep/文案/测试）
- 变更/设计文档与 `scripts/publish-dual-readme-release.ps1`
