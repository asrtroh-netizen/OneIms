# 2026-07-25 · Caller Phase-1 本机举报

## 范围

按 `docs/product/2026-07-25-onetools-caller-report-feedback-pipeline.md` Phase 1：

- 举报 → `LocalReportStore`
- 默认立刻写 `CallRule` BLOCK + `onespam` **单号 upsert**（不整库替换）
- 可撤销
- **不上云**

## 文件

- `LocalReportStore.kt` / `ReportApplier.kt`
- `SpamPackInstaller.upsertOne` / `removeOne`
- `CallerPrefs.applyReportLocally`（默认 true）
- `CallerScreen` 举报区

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
