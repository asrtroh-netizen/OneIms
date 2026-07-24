# OneTools 架构蓝图 · 方案 A（姊妹工具 App）

> 状态：**已拍板可执行**（v0.3 · UI 复用纠偏）  
> 日期：2026-07-25  
> 视角：资深全栈架构师  
> 产品规格：`docs/product/2026-07-24-onetools-initiation.md`  
> 拍板：定位 **方案 A** · 独立 Android App · 与 OneIMS 解耦  
> **纠偏（2026-07-25）**：① 首页四态 + **必须 Shizuku** ② **配色与 OneIMS 一致** ③ **第一页 UI 直接拉 OneIMS（OneLink/Shizuku 线）**

---

## 0. 一句话

**OneTools** = One 生态姊妹工具 App：首页 **直接复用 OneIMS（Shizuku/OneLink 线）第一页 UI + 同一套配色 token**，通道走 Shizuku 四态总控；其下可挂工具能力。**不写运营商配置**；视觉与首屏不另起炉灶。

---

## 1. 目标与非目标

### In（架构必须支撑）

| ID | 能力 | 模块归属 |
|---|---|---|
| F0 | **首页 UI = 直接移植 OneIMS OneLinkHome**（四态 StatusHero + 同结构区块） | `ui` ← 源 `HomeScreen.OneLinkHome` |
| F0b | **配色 = OneIMS Theme / Tokens 原样**（含 primary 白、动态色开关语义） | `ui/theme` ← 源 `Theme.kt` |
| F1 | 独立应用壳（包名 OneTools）+ 文案品牌替换处最小化 | `ui` / `app` |
| F2 | 设备摘要卡（只读、可复制） | `device` |
| F3 | 通道助手：跳转官方/修缮版 Shizuku、授权引导、失败降级说明 | `channel` |
| F4 | 一键导出诊断文本（本地；含通道四态快照） | `export` |
| F5 | 与 OneIMS 关系说明页 | `ui` |
| F6 | 隐私边界声明 | `ui` / `policy` |

### Out（架构硬墙 · 禁止落入代码路径）

- CarrierConfig / VoLTE / VoWiFi / VoNR **写入**（仍归 OneIMS）
- **内嵌 OneBridge / 自研无线调试配对栈**（OneTools 走 Shizuku，不复制 OneKuku 重通道）
- 会员支付闭环、插件市场、Root 提权实现、iOS

**守卫规则**：不得 `implementation(project(":bridge"))`；不得复制 OneIMS 写配 Service；**允许且必须**接入 `dev.rikka.shizuku:api/provider`（对齐 OneLink）。

---

## 2. 仓库与工程拓扑

### 决策：同 monorepo 新 module（推荐）

| 选项 | 说明 | 结论 |
|---|---|---|
| A1 同仓 `:onetools` | 共享 Gradle 镜像、签名脚本、作者工作流；进程/包名仍隔离 | **采用** |
| A2 独立新仓 | 发布节奏完全独立；双仓契约易漂 | 后期若商店/团队拆分再迁 |

当前 `settings.gradle.kts` 仅有 `:app` / `:bridge`。脚手架阶段：

```kotlin
// settings.gradle.kts（规划，尚未落地）
include(":onetools")
```

**禁止**：`:onetools` → `:bridge` 依赖；`:onetools` 不得 `implementation(project(":app"))`。

### 建议目录（落地脚手架时）

```
onetools/
  src/main/
    java/com/onetools/app/
      OneToolsApp.kt
      ui/          # Compose · 首页（四态框置顶）· 关系说明 · 隐私
      channel/     # Shizuku bridge · 四态 Policy · 激活引导
      device/      # DeviceSnapshot 采集（只读）
      export/      # Markdown/纯文本组装 · Share
      deeplink/    # 入站 URI 解析（可先 stub）
      policy/      # 权限请求策略 · 文案键
    AndroidManifest.xml  # ShizukuProvider + queries(Shizuku/OneIMS)
    res/
```

---

## 3. 进程 / 包名 / 品牌边界

| 项 | OneIMS | OneTools |
|---|---|---|
| 对外名 | OneIms（OneKuku / Lite） | **OneTools** |
| applicationId | `com.oneims.app` / `com.oneims.onelink` | **`com.onetools.app`** |
| 职责 | IMS 配置 · 诊断 · 修复 | 首屏同构工具壳 + Shizuku 通道 · 摘要 · 导出 |
| 特权通道 | OneKuku=OneBridge / Lite=Shizuku | **Shizuku** · 对齐 OneLink |
| 首页 UI | `HomeScreen`（OneKuku / OneLink 分支） | **直接拉 OneLinkHome**（见 §7） |
| 配色 | `Theme.kt` · `OneImsTokens` · primary 白 | **同一套，禁止另起色板** |
| 图标/商店名 | OneIms | 商店名 **OneTools**；**首屏视觉不强制换肤**（用户要求一致） |

副标题建议（商店短描述）：「OneIMS 配套工具 · 设备摘要与取证导出」。

---

## 4. 技术选型与权衡

| 层 | 选型 | 理由 | 不选 |
|---|---|---|---|
| 语言/UI | Kotlin + Jetpack Compose | 与 OneIMS 一致 | 另栈 |
| 最低 SDK | 对齐 OneIMS 现网 `minSdk` | 姊妹 App 可同机安装 | 盲目抬高 |
| 架构风格 | 单 Activity + Navigation；轻量 VM | MVP 够用 | Clean 七层 |
| DI | 先手写；复杂再 Hilt | YAGNI | 首日重 DI |
| 存储 | DataStore 偏好；无云库 | 本地优先 | Room/云同步 |
| 网络 | MVP 默认无网络客户端 | 降审查面 | 首版 Retrofit |
| 主题 | **原样复用** `OneImsTheme` + `OneImsTokens` | 「配色跟之前的保持一致」 | 新品牌色板 |
| 主色事实 | `primary = Color.White`（`2026-07-18-global-primary-white`） | 与现网一致 | 擅自恢复 Google Blue |
| 动态色 | 与 OneIMS 相同开关语义 | 行为一致 | 静默强制开关 |
| 首屏组件 | **移植 `OneLinkHome` + `StatusHero` 等依赖** | 「第一页 UI 直接拉 OneIMS」 | 重画 / 只抄语义不抄布局 |
| 特权 | Shizuku api/provider（对齐 OneLink） | 硬需求 | OneBridge / `:bridge` |
| 四态 | Policy + UI 一并带走 | 与首屏移植一致 | 另造进度叙事 |
| 测试 | 服从仓库惯例；优先真机四态手测 | — | 空测框架 |

---

## 5. 模块职责与依赖方向

```
ui ──► channel   (四态框 · Shizuku 绑定)
ui ──► device
ui ──► export
ui ──► deeplink
ui ──► policy

export ──► device + channel   (组装快照含四态)
channel ──► policy            (未装 Shizuku / 未授权降级)

禁止：device/export 反向依赖 ui
禁止：依赖 OneIMS :app / :bridge
允许：Maven 依赖 rikka.shizuku
```

| 模块 | 单一职责 | 对外类型 |
|---|---|---|
| `channel` | Shizuku binder/授权 + **四态收敛** + 打开 Shizuku App | `ChannelCardState` · `ShizukuGate` |
| `device` | 采集只读快照 | `DeviceSnapshot` |
| `export` | 渲染文本 + `ACTION_SEND` | `ExportDocument` |
| `deeplink` | 解析 `onetools://` | `DeepLinkTarget` |
| `policy` | 权限与降级文案 | `PermissionPlan` |

### 四态语义（与 OneIMS 对齐 · 契约真源）

证据：`app/.../OneKukuCardState.kt` · `docs/changes/2026-07-16-four-state-channel-card.md`

| 态 | 含义 | 典型条件（Shizuku 线） |
|---|---|---|
| `INACTIVE` | 未激活 | binder 未就绪或未授权 |
| `ACTIVATING` | 激活中 | 引导/等待 Shizuku Active + 用户授权 |
| `READY` | 就绪 | binder+授权且 App 前台 |
| `SLEEPING` | 休眠 | binder+授权且 App 退后台（划掉不必重配对） |

收敛策略建议直接移植 `OneKukuCardPolicy.resolve(serviceReady, isExecuting, channelSleeping)` 语义，避免「看起来像四态、规则却漂移」。

**复用策略（首屏 · 用户已拍板「直接拉过来」）**：

| 路径 | 做法 | 结论 |
|---|---|---|
| **R0 整页移植** | 以 `HomeScreen.kt` 的 **`OneLinkHome`** 为真源，连同 `StatusHero` / `OneImsPage` / 相关 string·drawable·palette **拷入 `:onetools` 并改包名**；去掉 IMS 写配专属入口 | **本期默认** |
| R1 只复制 Policy | 仅四态枚举 | 不足（用户要整页 UI） |
| R2 抽 `:channel-ui` / `:ui-common` | 供 `:app` 与 `:onetools` 共享 Theme+Home | 可选二期；现保护 OneIMS 冻结主线 |

证据锚点：

- 配色：`app/.../ui/theme/Theme.kt` · `OneImsTokens` · primary 白变更文档  
- 首页分支：`HomeScreen` → Shizuku 线走 `OneLinkHome`（`ChannelLine.usesShizuku`）  
- 四态：`OneKukuCardState` / `OneKukuCardPolicy` · `StatusHero` in `OneImsComponents.kt`

---

## 6. 跨端 / 跨 App 契约

### 6.1 导出摘要 Schema（v1 · 本地文件真源）

导出为 Markdown/纯文本，字段稳定、**只增不改语义**：

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `schemaVersion` | string | 否 | 固定 `"1"` |
| `exportedAt` | string | 否 | ISO-8601 UTC |
| `manufacturer` / `model` / `device` | string | 否 | Build.* |
| `androidVersion` / `sdkInt` | string / string | 否 | sdkInt **字符串**防跨端精度问题 |
| `securityPatch` | string | 可 | |
| `simSlots[]` | array | 可空数组 | 无 SIM → `[]`，不省略 |
| `simSlots[].carrierName` | string | 可 | |
| `simSlots[].mccMnc` | string | 可 | |
| `oneImsInstalled` | string | 否 | `"onekuku"` / `"onelink"` / `"both"` / `"none"` |
| `shizukuInstalled` | string | 否 | `"yes"` / `"no"` / `"unknown"` |
| `channelCardState` | string | 否 | `INACTIVE` / `ACTIVATING` / `READY` / `SLEEPING` |
| `channelHints` | string | 可 | 人类可读检查结果 |
| `notes` | string | 可 | 用户附加 |

**禁止**：导出默认上传；禁止在未授权时写外置敏感路径。

### 6.2 Deep Link（MVP 预留）

| 方向 | URI（草案） | 行为 |
|---|---|---|
| OneIMS → OneTools | `onetools://tool/export` · `onetools://home` | 打开对应页；未安装 → 商店/说明 |
| OneTools → OneIMS | 包名显式 Intent（以 OneIMS 侧现有入口为准） | 打开主页 |
| OneTools → Shizuku | 官方 / asrtroh 修缮版包名 Intent | 激活引导主路径 |

契约原则：版本字段可演进；未知 path **安全降级到首页四态框**，不崩溃。

### 6.3 包可见性

Manifest `queries` 必须声明：

- OneIMS 双包：`com.oneims.app` · `com.oneims.onelink`
- Shizuku：官方 `moe.shizuku.privileged.api` + 修缮版实际包名（以现网 asrtroh 包为准，脚手架时钉死）

遵守 Android 11+ 包可见性。

---

## 7. 信息架构（首页 · v0.3）

**第一页 = OneIMS OneLink 首页结构原样**，不是「品牌英雄 + 导出 CTA」另构图。

典型区块顺序（以现网 `OneLinkHome` 为准，移植时按源码排，不凭记忆重排）：

1. 顶栏 / SIM 选择（若 OneLink 有）  
2. **StatusHero 四态通道卡**  
3. 快速开始 / 设备详情等 OneLink 既有区块  
4. OneTools 增量工具（导出等）——**仅在不破坏原布局的前提下追加**，默认放原首页已有工具区之后  

文案替换：`channel_display_name` / 副标题等改为 OneTools 品牌字符串；**颜色、圆角、间距、英雄卡视觉不得改。**

品牌防混淆：靠 **应用名 / 商店列表 / 关于页**，不靠首屏换色。

---

## 8. 安全 · 隐私 · 权限

| 权限/能力 | 时机 | 拒绝路径 |
|---|---|---|
| Shizuku binder + 用户授权 | 首页激活主路径 | 停在 `INACTIVE`/`ACTIVATING`，文案引导安装/打开 Shizuku |
| 电话/短信相关（若需运营商名） | 进入摘要或导出前渐进索取 | 降级展示「未授权」字段，不崩溃 |
| 存储/媒体 | 仅当分享/保存失败需用户选目录时 | 取消干净 |
| 通知 | MVP 不需要 | — |

红线：Shizuku 仅用于 OneTools 声明的工具能力，**不得**借通道写运营商配置；日志与导出默认脱敏。

---

## 9. 性能与可扩展性

| 项 | 目标 |
|---|---|
| 冷启动 | ≤ 2s 到首屏可用（AC-1）；binder 未就绪不得假显示 READY |
| 导出 | 主线程外组装；超大截断 + 体积提示 |
| 扩展轴 | 新工具挂在四态框下方；不改四态语义除非双端同步 bump |

方案 C（伞品牌）所需的插件加载器 **本期不预埋接口**，避免空想抽象。

---

## 10. 发版与兼容

| 项 | 策略 |
|---|---|
| 版本号 | OneTools **独立** semver，不与 OneIMS 3.0.2 强绑 |
| 发版节奏 | 可单发；不进入 OneIMS 双包强制同发 SOP |
| 回滚 | 普通 App 卸载/降级；无 DB 迁移债（MVP） |
| 与 OneIMS / Shizuku 共存 | 同机可装；文案禁止「替代 IMS」；推荐 asrtroh Shizuku（与 README 友情推荐一致） |

---

## 11. MVP 落地顺序（Execute 清单 · 纠偏后）

| 步 | 内容 | 完成标准 |
|---|---|---|
| 1 | `:onetools` 壳 + **原样拷贝 `Theme.kt` / Tokens** + Shizuku 依赖 | 主题色与 OneIMS 一致；能 assemble |
| 2 | **整页移植 `OneLinkHome` + StatusHero 依赖链** | 真机首屏与 OneIMS Lite 同构 |
| 3 | Shizuku 激活/授权主路径（对齐 OneLink） | 四态可切换；禁假 READY |
| 4 | 剥除 IMS 写配专属入口；保留通道/设备区块 | AC-5 无写配路径 |
| 5 | 追加导出等工具（不改原布局节奏） | Telegram 可发送 |
| 6 | 品牌字符串 / 关于页区分 OneTools | 商店名清晰；首屏不换色 |

验证命令（脚手架后）：

```powershell
./gradlew :onetools:assembleDebug
# 若复制了 Policy：移植/改写 OneKukuCardPolicyTest 断言四态映射
```

---

## 12. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 与 OneIMS 品牌/四态混淆 | 高 | 独立图标/文案；关系页写清「工具≠写配」 |
| 范围漂成第二 IMS | 高 | Out 硬墙：禁写配、禁 `:bridge` |
| 四态语义漂移 | 高 | R1 单测锁 Policy；或升 R2 共享 |
| Shizuku 未装/版本碎片 | 中 | 引导官方 + asrtroh；假 READY 禁止 |
| monorepo 误改 `:app` | 中 | 改动审查限定 `onetools/` |
| 商店对调试/特权文案敏感 | 中 | 明示依赖 Shizuku；不做隐蔽 ADB |

---

## 13. 附录 · 需求八维（纠偏后）

| 维度 | 结论 |
|---|---|
| 🎯 表层需求 | 配色与 OneIMS 一致；第一页 UI 直接拉 OneIMS（OneLink） |
| 💡 深层意图 | 降低学习成本，One 生态同脸；OneTools 差在工具能力与包名 |
| 📎 必须处理 | Theme 原样 · OneLinkHome 整页移植 · Shizuku · 剥写配 |
| 🚀 查缺补漏 | 移植遗漏依赖、string 资源、假 READY、写配入口残留 |
| ✨ 顺手优化 | 导出 schema 已含四态字段 |
| 🧠 头脑风暴 | R0 整页移植（默认）/ R2 共享 module（二期） |
| 💩 屎山规避 | 不另起色板；不重画首页；不引入 `:bridge` |
| ⚠️ 注意事项 | 首屏同构会提高品牌混淆风险——用商店名/关于页区分 |
