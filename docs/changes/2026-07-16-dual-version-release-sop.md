# 2026-07-16 · 双版本同步发版 SOP（硬规矩）

## 用户定调

> **以后 OneKuku + OneLink 必须一起更新** — README、GitHub Release APK、版本号三者同步，禁止只发单线。  
> **2026-08-05 补刀：以后连 README** — 发版默认必须把 `README.md` 同步到 `origin/main`；禁止「只挂 APK、不推 README」。  
> **2026-08-05 再定调：下次发版直接跑 publish 脚本；不发源码；公开面仅仅是 APK + README。**

## 公开面允许上传什么

| 允许 | 禁止 |
|---|---|
| Release 双 APK | 把 `app/`、`bridge/`、私有分支 tip 推到 `main` |
| `origin/main` 上的 `README.md`（only） | 发版流程里 `git push` 全量源码 / 私有分支 |

日常源码仍可留在本地私有分支；**发版脚本路径不得夹带源码推送**。

## 每次发版清单（顺序固定）

| 步 | 动作 | 产物 / 门禁 |
|---|---|---|
| 1 | 升 `app/build.gradle.kts` 的 `oneImsVersionName` / `versionCode` | 两 flavor 共用同一 versionName 基线 + suffix |
| 2 | 更新 `README.md` What's New + 双版本下载直链 | 表格含 OneKuku / OneLink 两行；**本步不可省** |
| 3 | `./gradlew :app:packageDualDebugApks` | `OneIms-OneKuku-standalone-{ver}.apk` + `OneIms-Lite-Shizuku-{ver}.apk` |
| 4 | `gh release create/upload v{ver}` 上传**双包** | Release 资产必须含两条 APK |
| 5 | `git worktree` 仅 push `README.md` → `origin/main` | **硬门禁**；脚本会做 preflight/postflight 版本字串校验 |
| 6 | 本地 `docs/changes/` 记一笔 | 便于回溯 |

## 一键脚本

```powershell
.\scripts\publish-dual-readme-release.ps1 -Version 3.3.0
```

参数：

- `-SkipBuild` — 已有 APK 时跳过 Gradle
- `-SkipApkUpload` — 只推 README（仍会校验 README 含本版号）
- **不要**随便 `-SkipReadmePush`：默认拒绝；紧急跳过必须同时加 `-IKnowReadmeIsMandatoryAnyway`

## 命名约定

| 线 | APK 文件名 | 包名 |
|---|---|---|
| OneKuku | `OneIms-OneKuku-{version}.apk` | `com.oneims.app` |
| OneLink | `OneIms-OneLink-{version}.apk` | `com.oneims.onelink` |

## 公开仓边界

- `origin/main`：**README-only**（闭源发布仓）
- 源码：本地全量分支（现 `oneims-private-full`），禁止 `git push origin main` 裸推源码
- **禁止删除远程仓库**（会丢 Star / Release）；完整约定见 [`docs/guides/github-public-repo-conventions.md`](../guides/github-public-repo-conventions.md)


## 参考实例

- README 双版本备注：`f703fa5`
- 双包直链 + APK：`a0c3258` + Release `v2.2.0` 资产
