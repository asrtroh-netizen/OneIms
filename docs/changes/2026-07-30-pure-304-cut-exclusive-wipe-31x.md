# 2026-07-30 · 纯血 3.0.4 出包：砍独家三项 + 清 3.0.9/3.1.x 产物

## 决策（用户原话口径）

1. 以**纯血底包 3.0.4**为根，砍掉独家三项，**保留能力页信号显示**（5G信号强度调整），打出一版。
2. **彻底删除**本地 `3.0.9` / `3.1.0` / `3.1.1` 产物（APK）。

## 独家三项（不变）

1. 信号格显示样式  
2. 5G 显示增强  
3. 控制中心快捷切卡  

能力页第五项「5G信号强度调整」**不属于**三项独家，必须保留。  
真源：`2026-07-30-oneims-r2-cut-exclusive-three.md` · `2026-07-30-restore-capabilities-signal-strength.md` · `docs/architecture/2026-07-30-oneims-3.0.4-base-package.md`

## 本轮做了什么

| 项 | 结果 |
|---|---|
| 源码 R2 收敛 | **已在 main HEAD**（`4ecb42e08` + `a64eb01de`）；独家页无三项入口；能力页保留信号强度 |
| 工作树版本漂移 | 曾短暂 `3.1.1` / `versionCode 88` → **收回** `3.0.4` / `74`（与 HEAD 一致） |
| 删除 APK 产物 | 主仓 `release/v3.1/*3.1.0*.apk`；worktree `OneIMS-v31` 根目录 4 枚 `3.1.0` APK；空目录 `release/v3.1` 已删 |
| 出包 | 先 stash 隔离工作树 Broker/ShellUi WIP，再 `./gradlew :app:clean :app:packageDualDebugApks`，出包后 stash pop 恢复 WIP |

## 产物（本地，未发包 · 对齐 main HEAD，不含 Broker WIP）

| 文件 | package | versionName | versionCode | 大小（约） |
|---|---|---|---|---|
| `OneIms-OneKuku-standalone-3.0.4.apk` | `com.oneims.app` | `3.0.4-onekuku` | `74` | 37.6 MB |
| `OneIms-Lite-Shizuku-3.0.4.apk` | `com.oneims.onelink` | `3.0.4-onelink` | `74` | 26.7 MB |

（另有同内容的 `*-debug.apk` 副本。APK 内 `ShellUiAutomation*` 类计数 = 0。）

## 明确未做

- 未新建 / 未上传 GitHub Release（公开仍指 `v3.0.8`）
- 未删除 git 分支 `release/v3.1` 与 worktree `E:\GQ\One\OneIMS-v31`（源码线仍在；仅清 APK 产物）
- 未动工作树内无关的 Broker / Sandbox 未提交改动
- 未删除历史 changelog 考古叙述

## 验证

```text
aapt dump badging OneIms-OneKuku-standalone-3.0.4.apk
→ versionCode='74' versionName='3.0.4-onekuku'

aapt dump badging OneIms-Lite-Shizuku-3.0.4.apk
→ versionCode='74' versionName='3.0.4-onelink'

./gradlew :app:testOnekukuDebugUnitTest --tests com.oneims.app.core.CompatAndSocPolicyTest
→ BUILD SUCCESSFUL

rg -n "five_g_display|signal_bar_style|qs_tile_feature|DataSimSwitch" app/src/main/java/com/oneims/app/ui/ExperimentalScreen.kt
→ 无匹配

rg -n "signalStrengthAdjustmentEnabled" app/src/main/java/com/oneims/app/ui/CapabilitiesScreen.kt
→ 有（第五项）
```

## 安装注意

机上若已装更高 `versionCode`（含曾装的 3.1.x / 3.0.8=`78`），覆盖装 `74` 会被系统拒绝 → **先卸载再装**。
