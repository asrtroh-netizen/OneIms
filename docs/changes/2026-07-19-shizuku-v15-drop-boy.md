# Shizuku：去掉 Boy 痕迹并定版 V15.0.0

日期：2026-07-19  
工作树：`E:\GQ\One\_forks\HSSkyBoy-Shizuku-clean`

## 做了什么

1. 删除源码中对 HSSkyBoy 的注释归因（`BootCompleteReceiver`）
2. 应用版本固定为 `versionName=V15.0.0`、`versionCode=150000`（不再拼接 git r 后缀）
3. API 协议版本仍由 `api/manifest.gradle.kts` 维护（未改）

## 未改（有意）

- `.gitmodules` 仍指向 `HSSkyBoy/Shizuku-API`：本地 `api` 子模块有既有改动，贸然换远端会破坏同步；属仓库元数据而非 App 代码
- OneIMS 历史变更文档中的 Boy 对照记录：属文档史，非运行时代码

## 验证

- `:manager:compileReleaseKotlin` BUILD SUCCESSFUL（JDK 21）
