# 2026-07-25 · Shizuku 静默安装 + GitLab / F-Droid 源

## 需求

用户下一刀：① Shizuku 静默安装 ② GitLab / F-Droid 源。

## 落地

| 能力 | 实现 |
|---|---|
| 静默安装 | `ShizukuApkInstaller`：经 `ShizukuBinderWrapper` 取 `IPackageInstaller`，Session 写入 APK 后 commit；失败回退系统 Intent |
| 多源 | `AppSource` + `UpdateFetcher`：GitHub / GitLab Releases / F-Droid `api/v1/packages` |
| UI | 添加源可选源类型与 host；安装按钮在通道就绪时显示「静默」文案 |
| 权限 | Manifest 增加 `moe.shizuku.manager.permission.API_V23` |

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
BUILD SUCCESSFUL
```

真机静默装 / 真实 GitLab·F-Droid 拉取：**NOT RUN**（需通道与网络）。
