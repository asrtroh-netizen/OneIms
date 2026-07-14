# 2026-07-14 · 内置 OneKuku 核心 APK

## 变更
- 将 Shizuku v13.6.0 release APK 放入 `app/src/main/assets/onekuku-core.apk`
- `.gitignore` 例外允许提交该 assets 包
- 未安装核心时优先 `installBundledApk`，不再依赖外网下载才能起步
- 版本 2.0.14 / versionCode 23

## 说明
安装后系统里仍显示上游包名（过渡期兼容）；换皮包需另构建 BRANDED_CORE_PACKAGE。
