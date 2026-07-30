# 2026-07-30 · OneIMS / 周边线进展快照

> ⚠️ **版本身份已过时**：下文采证时本地号仍为 3.0.9；其后已废止，现回底包 **3.0.4 / vc74**（`2026-07-30-abolish-local-3.0.9.md`）。其余进度条目仍可作当日考古。

**采证时刻**：2026-07-30 约 16:35（UTC+8）  
**范围**：只读盘点（本轮未改业务代码）  
**本仓 HEAD**：`a21342afe`（`main`）

---

## 一句话

本地 **OneIMS**（采证时称 3.0.9，**后已废止**）已合入血统门禁 + Compose Indication 加固；小米线 **ShizukuDropIn-Local V15.1.0** Release APK 已产出并装过机，冷启仍被 MIUI WakePath 拦；工作树仅剩 **英文文案 2 处未提交** + 若干临时目录。

---

## OneIMS 主仓

| 项 | 状态 | 证据 |
|---|---|---|
| 版本（采证时） | 曾 `3.0.9` / `79` → **现** `3.0.4` / `74` | `abolish-local-3.0.9` + `app/build.gradle.kts` |
| 公开发包 | **未发** 3.0.9（且该本地号已废止）；公开仍指 3.0.8 | `bump-3.0.9`（已作废） / README |
| 血统 3.0.4 非 Tensor → UNSUPPORTED | **已合入 main** | `5d3a84641` merge + `cb92ade87` 文案对齐 |
| Compose `clickable` / Indication 加固 | **已提交** | `3231f6539`；说明见 `2026-07-30-compose-indication-clickable-crash.md` |
| 崩溃归属更正 | 用户栈在 **Shizuku**，非 OneIms；OneIms 加固为防御 | 会话 [XJ033 Compose归属](e0e25663-c055-407f-92aa-a889b93b18af) |
| DropIn 终版定义冻结 | **已文档冻结** | `a21342afe` + `2026-07-30-shizuku-dropin-local-vs-v15.md` |
| 未提交 | `values-en/strings.xml` 两处 Tensor 文案仍写「OEM decides」旧语义 | `git diff HEAD -- …/values-en/strings.xml` |
| 脏目录（未跟踪） | `.tmp_pixel_volte_patch_src/` `.tmp_ui_walk/` `.tmp_vvb2060_ims/` | `git status` |
| 与 `origin/main` | **分叉**：本地相对远端 ahead≈410 / behind≈44（含远端 README 同步提交） | `git rev-list --count` |

---

## ShizukuDropIn-Local（小米线，邻仓）

| 项 | 状态 | 证据 |
|---|---|---|
| 路径 | `E:\GQ\One\_forks\ShizukuDropIn-Local` | 既定约定 |
| HEAD | `bfd4070`「增加稳定性」 | `git log -1` |
| 身份 | `V15.1.0` / `151000` | `build.gradle` `rootProject.ext` |
| 终版策略 | Plus 底 + V15 皮 + 白名单 + V15 特性；入口 `V15SkinHomeActivity` | 文档 §冻结 + `aa8d31c` |
| Release 构建 | **PASS**（日志 `BUILD SUCCESSFUL`；APK ≈6.4MB 仍在） | `manager/build/outputs/apk/dropin/release/manager-dropin-release.apk` |
| 真机安装（历史） | **PASS**（`22061218C`） | DropIn 比较文档 §8 |
| 冷启广播 | **FAIL**：MIUI `Security_WakePath Restrict` SKIPPED | 同上；需自启动放行后再测 |
| 中途构建诊断 | XJ034 曾报卡在 R8；**后续已出包**，该中途状态已过时 | [XJ034 构建诊断](0ac5f19c-aaaa-442e-bf81-d0a4b1b654e7) + 现有 APK |

---

## 策略矩阵（既定，未改）

| 机型 | 用哪条 |
|---|---|
| Pixel | V15 clean（`HSSkyBoy-Shizuku-clean`） |
| 小米 / 重 OEM | DropIn-Local |

---

## 未闭环 / 风险

1. OneIMS 英文血统文案未提交（中文已在 `cb92ade87`）。
2. `main` 与 `origin/main` 分叉，推送前需显式合入/rebase 远端 README 同步，禁止 blind force-push。
3. 小米冷启依赖用户「自启动」放行；服务真拉起二次复测仍为历史 NOT RUN。
4. 3.0.9 本地号未发版；公开下载区仍指向 3.0.8（按约定）。
5. 临时逆向/UI 走查目录勿误提交。
