# 2026-07-16 · 双版本同步发版 SOP（硬规矩）

## 用户定调

> **以后 OneKuku + OneLink 必须一起更新** — README、GitHub Release APK、版本号三者同步，禁止只发单线。

## 每次发版清单（顺序固定）

| 步 | 动作 | 产物 / 门禁 |
|---|---|---|
| 1 | 升 `app/build.gradle.kts` 的 `oneImsVersionName` / `versionCode` | 两 flavor 共用同一 versionName 基线 + suffix |
| 2 | 更新 `README.md` What's New + 双版本下载直链 | 表格含 OneKuku / OneLink 两行 |
| 3 | `./gradlew :app:packageDualDebugApks` | `OneIms-OneKuku-{ver}.apk` + `OneIms-OneLink-{ver}.apk` |
| 4 | `gh release upload v{ver}` 上传**双包** | Release 资产必须含两条 APK |
| 5 | `git worktree` 仅 push `README.md` → `origin/main` | **绝不推源码** |
| 6 | 本地 `docs/changes/` 记一笔 | 便于回溯 |

## 一键脚本

```powershell
.\scripts\publish-dual-readme-release.ps1 -Version 2.2.0
```

参数：

- `-SkipBuild` — 已有 APK 时跳过 Gradle
- `-SkipApkUpload` — 只推 README
- `-SkipReadmePush` — 只上传 APK

## 命名约定

| 线 | APK 文件名 | 包名 |
|---|---|---|
| OneKuku | `OneIms-OneKuku-{version}.apk` | `com.oneims.app` |
| OneLink | `OneIms-OneLink-{version}.apk` | `com.oneims.onelink` |

## 公开仓边界

- `origin/main`：**README-only**（闭源发布仓）
- 源码：本地 `main` 全历史，禁止 `git push origin main` 裸推

## 参考实例

- README 双版本备注：`f703fa5`
- 双包直链 + APK：`a0c3258` + Release `v2.2.0` 资产
