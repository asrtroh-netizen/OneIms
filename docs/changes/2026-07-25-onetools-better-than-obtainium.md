# 2026-07-25 · OneTools 更新器超越 Obtainium

## 目标

用户：「不仅要做出来，还要比他做的更好」→ 在自研 GitHub 更新目录之上，补齐并强化关键体验点。

## 相对 Obtainium 的增强（本轮）

| 能力 | 说明 |
|---|---|
| ABI 智能选包 | `ApkAssetPicker` 按 `Build.SUPPORTED_ABIS` 打分，惩罚错架构 |
| 版本状态 | `VersionCompare` → 可更新 / 已最新 / 未安装；列表可更新优先 |
| 添加源校验 | `validateRepo` 确认 GitHub 仓库 HTTP 200 |
| Release 说明 | 展示 GitHub Release body |
| 目录导入导出 | JSON 剪贴板导出 + 合并导入 |
| 自动绑定包名 | 下载 APK 后 `getPackageArchiveInfo` 回填 `packageName` |

未做（明确非本轮）：GitLab/F-Droid 多源、后台定时检查、Shizuku 静默安装。

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
BUILD SUCCESSFUL
```

含 `ApkAssetPickerTest` / `VersionCompareTest` / `CatalogExportTest` / `GitHubRepoParserTest`。
