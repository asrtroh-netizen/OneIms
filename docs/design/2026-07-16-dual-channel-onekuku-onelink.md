# 2026-07-16 · 双线产品：OneKuku / OneLink

**类型**：架构 + 发版 SOP（打包 / Release 需用户下令）  
**规模**：L · 第一期脚手架已落地，发版流程 2026-07-16 定稿

## 发版硬规矩（2026-07-16 定调）

> **OneKuku + OneLink 必须一起更新**：版本号、README、Release 双 APK 同步，禁止只发单线。  
> 详 SOP：`docs/changes/2026-07-16-dual-version-release-sop.md` · 脚本：`scripts/publish-dual-readme-release.ps1`

## 目标

正式发版时每次双包生成 → 双包 Release → README 直链（`origin/main` 仅 README）：

| 线 | applicationId | 通道 | 首页状态框 |
|---|---|---|---|
| OneKuku | `com.oneims.app` | 内置 OneBridge（现状） | **OneKuku** |
| OneLink | `com.oneims.onelink` | 官方 Shizuku | **OneLink** |

### 产品定调（硬约束）

> **OneLink = 轻量壳；重头全在官方 Shizuku。**  
> 体验锚点 = **2.0.8 / 2.0.9**（接入 OneKuku 内嵌配对栈之前的最后一个版本）：  
> 一点激活 → 打开官方 Shizuku（未装跳商店）→ 用户在 Shizuku 内配对/Start → 回 App 授权。  
> 不自建配对 / 内嵌 ADB / App 前台常驻 / OneBridge。IMS/恢复业务复用共用代码。

## 已落地（第一期）

1. Gradle `flavorDimensions += channel`：`onekuku` / `onelink`
2. `BuildConfig.CHANNEL_LINE` / `CHANNEL_USES_EMBEDDED_BRIDGE`
3. `ChannelBridgeBootstrap` 分 flavor 注入：`OneBridgePrivilegeBridge` vs `ShizukuPrivilegeBridge`
4. OneLink 恢复 `rikka.shizuku` api/provider（仅 `onelinkImplementation`）
5. OneLink Manifest 合并 `ShizukuProvider` + Shizuku 包查询
6. OneLink 资源覆盖首页关键 OneKuku 文案 → OneLink
7. `prepareOneKukuCore` 在 OneLink 线改走 `prepareOneLinkShizukuChannel`
8. 双包任务（**默认不跑**）：
   - `packageNamedOnekukuDebugApk`
   - `packageNamedOnelinkDebugApk`
   - `packageDualDebugApks`

## 发版操作备忘（双版本一起）

```powershell
# 完整链路（build + upload + README push）
.\scripts\publish-dual-readme-release.ps1 -Version 2.2.0

# 或分步
./gradlew :app:packageDualDebugApks
gh release upload v2.2.0 OneIms-OneKuku-2.2.0.apk OneIms-OneLink-2.2.0.apk --clobber
# README → origin/main 见 SOP（git worktree，仅 README.md）
```

> release 对称任务后续可补：`assembleOnekukuRelease` + `assembleOnelinkRelease`

## OneLink 精简矩阵（2026-07-16）

| 项 | 状态 | 说明 |
|---|---|---|
| Manifest 去掉 `BridgeBinderProvider` | ✅ 已做 | onelink `tools:node="remove"` |
| Manifest 去掉 `OneKukuResidentService` | ✅ 已做 | 常驻 FG 仅 OneKuku |
| Manifest 去掉 `WirelessPairingCodeReceiver` | ✅ 已做 | 六位码配对仅内嵌 ADB |
| `:bridge` 仅 `onekukuImplementation` | ✅ 已做 | 实现迁 `src/onekuku`；onelink 轻量桩 |
| libadb-android / conscrypt / sun-security 仅 onekuku | ✅ 已做 | runtimeClasspath 已核对 |
| `OneKukuBootRestoreService` / `BootReceiver` | 暂留 | OneLink 仍可借 Shizuku 做开机检查/恢复通话配置 |
| GuardService / QS Tiles / APN catalog | 暂留 | 业务共用，与通道无关 |
| 全库 `onekuku_*` 字符串键重命名 | 不做 | 无行为收益；用 flavor overlay |
| 英文 onelink overlay 完整集 | 待做 | 文案层 |
| CI 双包 Release | 待做 | 发版自动化 |

**结论**：第一刀（Manifest）+ 第二刀（依赖/源码）均已落地。  
`onelinkDebugRuntimeClasspath` 仅含 Shizuku；`onekukuDebugRuntimeClasspath` 含 `:bridge` + libadb 族。

## 刻意未做 / 后续

- CI 矩阵与 GitHub Release 资产双上传自动化
- 英文 onelink overlay 完整集
- 授权后打双包对比体积（会话禁打包）

## 风险

- IDE 需选择 flavor（`onekukuDebug` / `onelinkDebug`）；裸 `assembleDebug` 不再存在
- OneLink 依赖用户已安装并启动官方 Shizuku
- 两线可同机并存（不同 applicationId）
- onelink 仍保留同 FQCN 轻量桩（供 shared MainActivity 编译），无 bridge/libadb 实依赖
