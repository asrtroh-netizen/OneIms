# OneTools 架构蓝图 · 方案 A（姊妹工具 App）

> 状态：**已拍板可执行**（v0.2 · 纠偏）  
> 日期：2026-07-25  
> 视角：资深全栈架构师  
> 产品规格：`docs/product/2026-07-24-onetools-initiation.md`  
> 拍板：定位 **方案 A** · 独立 Android App · 与 OneIMS 解耦  
> **纠偏（2026-07-25）**：首页继承 OneIMS **四态通道框**；特权通道 **必须 Shizuku**（覆盖 v0.1「普通权限」默认）

---

## 0. 一句话

**OneTools** = One 生态下的 **姊妹配套工具 App**：首页以 **Shizuku 四态通道框**为总控，其下挂设备摘要 / 导出等工具；**不写运营商配置、不替代 OneIMS**，但 **必须能借助 Shizuku 获得特权通道**（体验对齐 OneIms Lite / OneLink 线）。

---

## 1. 目标与非目标

### In（架构必须支撑）

| ID | 能力 | 模块归属 |
|---|---|---|
| F0 | **首页四态通道总控卡**（未激活→激活中→就绪↔休眠）+ Shizuku 激活/授权 | `channel` / `ui` |
| F1 | 独立应用壳 + 首页（四态框置顶） | `ui` / `app` |
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
| 职责 | IMS 配置 · 诊断 · 修复 | 配套工具 + **Shizuku 通道总控** · 摘要 · 导出 |
| 特权通道 | OneKuku=OneBridge / Lite=Shizuku | **Shizuku（硬需求）** · 体验锚点对齐 OneLink |
| 首页锚点 | 四态通道卡 | **同一套四态语义**（见 §7） |
| 图标/色板 | 既有 | **必须区分**（防商店混淆） |

副标题建议（商店短描述）：「OneIMS 配套工具 · 设备摘要与取证导出」。

---

## 4. 技术选型与权衡

| 层 | 选型 | 理由 | 不选 |
|---|---|---|---|
| 语言/UI | Kotlin + Jetpack Compose | 与 OneIMS 作者栈一致，降低心智切换 | 新建 Flutter/RN 双栈 |
| 最低 SDK | 对齐 OneIMS（Pixel 主战场；以 `app` 现网 `minSdk` 为准，脚手架时抄齐） | 避免「姊妹 App 装不上」 | 盲目抬高 minSdk |
| 架构风格 | 单 Activity + Navigation Compose；轻量 `ViewModel` + 单向数据流 | MVP 功能面窄，拒绝 Clean 七层 | 过早 Domain/UseCase 爆炸 |
| DI | 可先手写工厂；模块 >3 再引入 Hilt | YAGNI | 首日 Hilt+多模块 |
| 存储 | DataStore 仅存用户偏好（主题/上次导出路径提示）；**无账号云库** | 本地优先 | Room/云同步 |
| 网络 | MVP **默认无网络客户端** | 降隐私与商店审查面 | 首版 Retrofit |
| 特权 | **Shizuku**（`rikka.shizuku` api/provider，版本对齐 OneLink 现网） | 用户硬需求；体验锚点 = OneIms Lite | 内嵌 OneBridge / `:bridge` |
| 四态 UI | 语义复用 `OneKukuCardState` 四态；组件可抽共享或受控复制 | 用户要求「继承之前产品的四态框」 | 另造五态/进度叙事 |
| 测试 | 项目若禁自动化测则标 NOT RUN；优先契约样例 + 真机手测清单 | 服从仓库惯例 | 为空测造框架 |

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

**复用策略（二选一，脚手架前拍板实现路径）**：

| 路径 | 做法 | 取舍 |
|---|---|---|
| **R1 受控复制** | `:onetools` 内复制 Policy + StatusHero 并改包名 | MVP 快；需单测锁四态，防漂移 |
| **R2 抽共享 module** | 新建 `:channel-ui` 供 `:app`(onelink) 与 `:onetools` 共用 | 长期干净；触及 OneIMS 重构，成本更高 |

**本期默认 R1**（保护 OneIMS 冻结主线）；若你明确要求共享抽取再升 R2。

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

## 7. 信息架构（首页预算 · 纠偏后）

首页一屏（继承 OneIMS 心智）：

1. **四态通道总控卡**（置顶 · 主交互）— 未激活 / 激活中 / 就绪 / 休眠  
2. 品牌条：**OneTools** + 一句「OneIMS 配套工具」  
3. 次级工具区（就绪/休眠时强调）：**导出诊断摘要** · 设备摘要  
4. 关系说明 / 隐私入口（不占首屏英雄位）

不做仪表盘、不做指标墙；**禁止**把导出 CTA 压过四态框。

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
| 1 | `:onetools` Gradle 壳 + Shizuku 依赖 + Manifest Provider | `assembleDebug` 出 APK |
| 2 | 四态 Policy + StatusHero（R1 复制）+ 首页置顶 | 真机四态可切换；退后台→休眠 |
| 3 | Shizuku 激活/授权主路径（对齐 OneLink 体验） | 未装/未授权有明确引导 |
| 4 | `DeviceSnapshot` + 摘要卡（四态下方） | 可读可复制 |
| 5 | Export Markdown（含 `channelCardState`）+ Share | Telegram 可发送 |
| 6 | 关系说明 + 隐私页 + `queries` | 文案过关；AC-5 无写配路径 |

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
| 🎯 表层需求 | OneTools 首页继承四态框，且必须借助 Shizuku |
| 💡 深层意图 | 工具产品仍要「通道总控」心智，与 OneIMS/Lite 一致，降低学习成本 |
| 📎 必须处理 | 覆盖「普通权限」默认；四态契约；Shizuku 依赖与引导；Out 仍禁写配 |
| 🚀 查缺补漏 | 未装 Shizuku、授权拒绝、退后台休眠、假 READY、四态漂移 |
| ✨ 顺手优化 | 导出 schema 增加 `channelCardState` / `shizukuInstalled` |
| 🧠 头脑风暴 | R1 复制 vs R2 抽 `:channel-ui` — 默认 R1 |
| 💩 屎山规避 | 不引入 `:bridge`；不在 OneTools 复制写配引擎 |
| ⚠️ 注意事项 | 用户口头覆盖 §8 决策台账第 6 条；OneIMS 主线冻结勿误伤 |
