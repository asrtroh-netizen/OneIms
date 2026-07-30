# 2026-07-30 · 本地 Shizuku vs thejaustin/ShizukuPlus `v13.6.0.r2185`

**对比对象**

| 侧 | 路径 / 引用 | 身份 |
|---|---|---|
| 本地主对照（项目约定） | `E:\GQ\One\_forks\HSSkyBoy-Shizuku-clean` | `versionName=V15.0.0`，`applicationId=moe.shizuku.privileged.api`，HEAD `1ba7389`（2026-07-19） |
| 本地旁证 | `HSSkyBoy-Shizuku`（`V15.0.3`）、`thedjchi-Shizuku`（`V15.0`） | 同族 V15 / thedjchi 线，非 ShizukuPlus |
| 远端 release | [thejaustin/ShizukuPlus@v13.6.0.r2185](https://github.com/thejaustin/ShizukuPlus/releases/tag/v13.6.0.r2185) | tag → commit `46f6fa27`；`versionName=Shizuku+ 13.6.0.r{gitCommitCount}` |

**结论一句话**：不是同一条产品线——本地是 **stock 包名 + V15 品牌** 的 HSSkyBoy/thedjchi 系 Manager；远端是 **Shizuku+ 增强 fork**（独立包名 + Drop-In 双风味 + Plus API/SU Bridge 等）。r2185 的「provider null 重试」本地 clean 仓已有更激进版本（最多 50 次 / 200ms），不必为这一条单独追 r2185。

---

## 1. 身份与包名

| 维度 | 本地 `HSSkyBoy-Shizuku-clean` | ShizukuPlus `v13.6.0.r2185` |
|---|---|---|
| 对外版本 | `V15.0.0`（`versionCode=150000` 固定） | `Shizuku+ 13.6.0.r2185`（`versionCode=gitCommitCount`） |
| Manager 包名 | 仅 `moe.shizuku.privileged.api` | Plus：`af.shizuku.plus.api`；Drop-In：`moe.shizuku.privileged.api` |
| 命名空间 | `moe.shizuku.*` / `rikka.shizuku.*` | `af.shizuku.*` |
| 与官方共存 | 与 stock Shizuku **同包名**，互斥安装 | Plus 可与 stock **并存**（靠 Compat Hub）；Drop-In 与 stock 互斥 |
| 上游自称 | HSSkyBoy fork + 本地 V15 冷启补丁 | README：fork of **thedjchi/Shizuku**，再叠 Plus 能力 |

证据：本地 `manager/build.gradle`；远端 `manager/build.gradle`（gh contents @ tag）、release 资产名 `Shizuku+-*.apk` / `Shizuku+-Drop-In-*.apk`。

---

## 2. 工程模块与工具链

| 维度 | 本地 clean | ShizukuPlus r2185 |
|---|---|---|
| 核心模块 | `server` `starter` `shell` `manager` `common` + `api/*` | 同上，另增 `:database` `:compat` `:app-process` `:core:ui` |
| AGP | `8.10.1`（settings） | `9.0.0`（settings.gradle） |
| 额外集成 | 无 Sentry / AboutLibraries 主路径 | Sentry、Spotless、AboutLibraries、Compose、productFlavors |
| Maven | 偏阿里云镜像（本机网络） | 标准 Google / Central + JitPack(libsu) |

---

## 3. r2185 当条变更 vs 本地

远端 release note：

> `fix(starter): retry binder handoff when manager provider is transiently null (#371)`  
> 文件：`starter/.../af/shizuku/starter/ServiceStarter.kt`  
> 行为：`getContentProviderExternal` 返回 null 时 **延迟 1s 再试一次**（与 dead-binder 路径对称）。

本地 `ServiceStarter.java`（`moe.shizuku.starter`）：

- provider == null 时已有循环重试：`MAX_RETRIES=50`，`RETRY_DELAY_MS=200`（约 10s 窗口）
- dead binder 仍走 `forceStopPackage` + 1s 再试

**因此**：就「OEM 杀进程后 provider 瞬时 null」这一痛点，本地 V15 clean **已覆盖且更激进**；差异在实现语言（Java vs Kotlin）、包名常量（`moe…` vs `af…`），不是「本地缺补丁」。

---

## 4. 功能面（远端有、本地无 / 弱）

来自 ShizukuPlus README（tag 内容）与模块名，本地 clean **未见对等实现**：

- Plus API 族（AVF、Storage、Intelligence、Overlay Manager Plus、Network/DNS Governor、Activity Manager Plus 等）
- Transparent Shell Interceptor（`pm`/`am`/`settings` 走原生 API）
- Local ADB Proxy（端口 15555）、**SU Bridge**（自定义 su 路径）
- Compat Hub（让第三方仍找 `moe.shizuku.privileged.api`）
- Modular Plus Features 开关、Dynamic App Database、`plus` CLI
- Android 16/17 Local Network / `deviceId` 兼容叙事（README 宣称；本地未做同名对照审计）

本地侧相对强项（项目文档 + 近期 commit）：

- OneIMS 对齐用的 **冷启 / Wi‑Fi / binder 就绪** 补丁（见 `docs/architecture/2026-07-29-onekuku-vs-shizuku-v15-alignment.md`）
- 品牌与版本叙事已切到 **V15**，不再跟 13.6.x 命名

---

## 5. 旁证仓库（勿混）

| 路径 | HEAD | 备注 |
|---|---|---|
| `HSSkyBoy-Shizuku` | `c245b156` · `V15.0.3` | 注释写对齐 Rikka official v13.6.0；仍是 HSSkyBoy 线 |
| `thedjchi-Shizuku` | `f06512c` · `V15.0` | ShizukuPlus 自称上游之一，但本地工作树停在 07-18 冷启补丁，**不是** Plus r2185 |

---

## 6. 验证账本

| 检查 | 结果 |
|---|---|
| `gh api` release / tag / commit / `build.gradle` / README / `ServiceStarter.kt` | PASS |
| 本地三仓 `git remote` / `versionName` / `applicationId` / `ServiceStarter.java` | PASS |
| `git clone` / 完整 zipball | FAIL（443 连接重置）；改走 Contents API |
| APK 完整下载 + SHA256 对账 | **NOT RUN**：本机拉到的文件未达 release 宣称 ~7.5MB；API 给出 digest `sha256:c5aca471…` / `sha256:7f65a6f2…` 可人工复核 |
| 真机安装共存 / 功能对照 | **NOT RUN** |

人工可选：

1. 浏览器下载两份 APK，校验 digest 与上表一致  
2. `aapt dump badging` 核对 `package` / `versionName`  
3. 若要合 Plus 能力，单独评估换包名 / Compat Hub，勿直接覆盖 V15 stock 包

---

## 7. 建议（架构向）

1. **继续用本地 V15 clean 作 OneIMS 对齐真源**；不要假设「升到 r2185」等于升级本地仓。  
2. 若只想拿 r2185 的 provider-null 修复：**不必合**——本地已有更强重试。  
3. 若想要 SU Bridge / Plus API / 与 stock 共存：应把 **ShizukuPlus 当另一产品** 评估（包名、签名、API 契约、维护成本），而不是 diff-merge 进当前 V15 树。
