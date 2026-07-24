# 2026-07-25 · OneTools 类 Obtainium 更新目录

## 背景

用户要 **Obtainium-like 能力内嵌、自研 Kotlin**（可任意添加 GitHub 源），不是合并 Flutter/GPL Obtainium，也不是只装官方 APK。

## 方案

- `UpdateCatalogRepository`：DataStore 持久化用户目录；空目录时写入 presets
- `GitHubRepoParser`：解析 `owner/repo` 与 GitHub URL（`java.net.URI`，避免 JVM 单测依赖 `android.net.Uri`）
- `UpdatesScreen`：添加/删除源、检查更新、下载安装
- presets：OneTools / OneIms 双线 / asrtroh Shizuku

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
BUILD SUCCESSFUL
```

- 单元测试含 `GitHubRepoParserTest`（owner/repo + URL）
- APK：`onetools/build/outputs/apk/debug/onetools-debug.apk`
