# 2026-07-25 · 更新器干净室对齐 Obtainium（开源侧）

## 许可

Obtainium = **GPL-3**。本仓 **零行** 并入其 Flutter/Dart 源码；仅干净室复刻公开行为。

## 本轮对齐

| 能力 | 实现 |
|---|---|
| 后台定时检查 + 通知 | WorkManager `UpdateCheckWorker`（默认 6h，可 3/12/24） |
| Forgejo/Codeberg | `ForgejoReleaseClient` |
| Direct APK | `DirectApkClient`（ETag/Last-Modified 作版本标记） |
| HTML 回退 | `HtmlApkClient` 抽 `.apk` href |
| APK 正则 / 预发布 | `TrackedApp.apkRegex` / `includePrereleases` |

## 明确未做（非「开源就能抄」）

APKPure / Aptoide / 华为商店等商业爬虫；完整 Obtainium UI 克隆。

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
