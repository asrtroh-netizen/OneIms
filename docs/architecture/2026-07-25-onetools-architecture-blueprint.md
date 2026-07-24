# OneTools 架构蓝图 · 方案 A（姊妹工具 App）

> 状态：**已拍板可执行**（v0.1）  
> 日期：2026-07-25  
> 视角：资深全栈架构师  
> 产品规格：`docs/product/2026-07-24-onetools-initiation.md`  
> 拍板：定位 **方案 A** · 独立 Android App · 与 OneIMS 解耦

---

## 0. 一句话

**OneTools** = One 生态下的 **姊妹配套工具 App**：设备摘要、通道引导、一键导出诊断材料；**不写运营商配置、不替代 OneIMS**。

---

## 1. 目标与非目标

### In（架构必须支撑）

| ID | 能力 | 模块归属 |
|---|---|---|
| F1 | 独立应用壳 + 极简首页 | `ui` / `app` |
| F2 | 设备摘要卡（只读、可复制） | `device` |
| F3 | 通道助手（检查清单 + 跳转系统页 + OneIMS 互唤说明） | `channel` |
| F4 | 一键导出诊断文本（本地、显式触发） | `export` |
| F5 | 与 OneIMS 关系说明页 | `ui` |
| F6 | 隐私边界声明 | `ui` / `policy` |

### Out（架构硬墙 · 禁止落入代码路径）

- CarrierConfig / VoLTE / VoWiFi / VoNR **写入**
- OneBridge / 内嵌 ADB / Shizuku 特权通道实现
- 会员支付闭环、插件市场、Root 提权、iOS

**守卫规则**：任何 PR 不得引入 `bridge` 模块依赖、不得复制 OneIMS 写配 Service；CI/审查对照本表。

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
      ui/          # Compose 导航 · 首页 · 关系说明 · 隐私
      device/      # DeviceSnapshot 采集（只读）
      channel/     # 检查清单 · Intent 跳转 · 深链出站
      export/      # Markdown/纯文本组装 · Share
      deeplink/    # 入站 URI 解析（可先 stub）
      policy/      # 权限请求策略 · 文案键
    AndroidManifest.xml
    res/
```

---

## 3. 进程 / 包名 / 品牌边界

| 项 | OneIMS | OneTools |
|---|---|---|
| 对外名 | OneIms（OneKuku / Lite） | **OneTools** |
| applicationId | `com.oneims.app` / `com.oneims.onelink` | **`com.onetools.app`** |
| 职责 | IMS 配置 · 诊断 · 修复 | 配套摘要 · 引导 · 导出 |
| 特权通道 | OneBridge / Shizuku | **无（MVP）** |
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
| 特权 | 无 | Out 清单 | 依赖 `:bridge` |
| 测试 | 项目若禁自动化测则标 NOT RUN；优先契约样例 + 真机手测清单 | 服从仓库惯例 | 为空测造框架 |

---

## 5. 模块职责与依赖方向

```
ui ──► device
ui ──► channel
ui ──► export
ui ──► deeplink
ui ──► policy

export ──► device   (组装快照)
channel ──► policy  (权限/跳转策略)

禁止：device/channel/export 反向依赖 ui
禁止：任何模块依赖 OneIMS :app / :bridge
```

| 模块 | 单一职责 | 对外类型 |
|---|---|---|
| `device` | 采集只读快照 | `DeviceSnapshot`（不可变 data） |
| `channel` | 检查项状态 + 系统 Intent | `ChannelChecklist` |
| `export` | 渲染文本 + `ACTION_SEND` | `ExportDocument` |
| `deeplink` | 解析 `onetools://` / App Links | `DeepLinkTarget` |
| `policy` | 权限与降级文案 | `PermissionPlan` |

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
| `channelHints` | string | 可 | 人类可读检查结果 |
| `notes` | string | 可 | 用户附加 |

**禁止**：导出默认上传；禁止在未授权时写外置敏感路径。

### 6.2 Deep Link（MVP 预留）

| 方向 | URI（草案） | 行为 |
|---|---|---|
| OneIMS → OneTools | `onetools://tool/export` · `onetools://tool/channel` | 打开对应页；未安装 → 商店/说明 |
| OneTools → OneIMS | `oneims://home` 或包名显式 Intent（以 OneIMS 侧现有入口为准，联调时钉死） | 打开主页/通道卡 |

契约原则：版本字段可演进；未知 path **安全降级到首页**，不崩溃。

### 6.3 包可见性

Manifest `queries` 声明 OneIMS 双包名，用于「是否已安装」检测；遵守 Android 11+ 包可见性。

---

## 7. 信息架构（首页预算）

首页一屏只承载：

1. 品牌 **OneTools**（主视觉）  
2. 一句定位：「OneIMS 配套工具」  
3. 一个主 CTA：**导出诊断摘要**  
4. 次要入口：通道助手 · 关系说明  

不做仪表盘、不做指标墙、不做卡片堆砌营销。

---

## 8. 安全 · 隐私 · 权限

| 权限 | 时机 | 拒绝路径 |
|---|---|---|
| 电话/短信相关（若需运营商名） | 进入摘要或导出前渐进索取 | 降级展示「未授权」字段，不崩溃 |
| 存储/媒体 | 仅当分享/保存失败需用户选目录时 | 取消干净 |
| 通知 | MVP 不需要 | — |

红线：日志与导出默认脱敏（不写完整 IMSI/ICCID 除非用户显式勾选进阶项——进阶项二期）。

---

## 9. 性能与可扩展性

| 项 | 目标 |
|---|---|
| 冷启动 | ≤ 2s 到首屏可用（AC-1） |
| 导出 | 主线程外组装；超大截断 + 体积提示 |
| 扩展轴 | 新工具 = 新 `feature/*` 模块或 `ui` 下独立路由；不改 `device` 核心模型除非 schema bump |

方案 C（伞品牌）所需的插件加载器 **本期不预埋接口**，避免空想抽象。

---

## 10. 发版与兼容

| 项 | 策略 |
|---|---|
| 版本号 | OneTools **独立** semver，不与 OneIMS 3.0.2 强绑 |
| 发版节奏 | 可单发；不进入 OneIMS 双包强制同发 SOP |
| 回滚 | 普通 App 卸载/降级；无 DB 迁移债（MVP） |
| 与 OneIMS 共存 | 同机可装；文案禁止「替代 IMS」 |

---

## 11. MVP 落地顺序（Execute 清单）

| 步 | 内容 | 完成标准 |
|---|---|---|
| 1 | `:onetools` Gradle 壳 + 空首页 Compose | 能 `assembleDebug` 出 APK |
| 2 | `DeviceSnapshot` + 首页摘要卡 | 真机字段可读可复制 |
| 3 | Export Markdown + Share | Telegram 可发送 |
| 4 | Channel checklist + 系统设置 Intent | 无特权仍可用 |
| 5 | 关系说明 + 隐私页 | AC-2 / AC-5 文案过关 |
| 6 | Deep link stub + `queries` | 未接通时降级说明页 |

验证命令（脚手架后）：

```powershell
./gradlew :onetools:assembleDebug
```

---

## 12. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| 与 OneIMS 品牌混淆 | 高 | 独立图标/文案/关系页 |
| 范围漂成第二 IMS | 高 | Out 硬墙 + 依赖禁令 |
| monorepo 误改 `:app` | 中 | 改动审查限定 `onetools/` + 本文档 |
| 商店对「无线调试引导」敏感 | 中 | 以系统设置跳转为主，不做隐蔽 ADB |

---

## 13. 附录 · 需求八维（拍板后）

| 维度 | 结论 |
|---|---|
| 🎯 表层需求 | 冻结 OneTools = 方案 A 姊妹 App，并给出可执行架构蓝图 |
| 💡 深层意图 | 主线冻结后扩展工具层，保护 OneIMS 语义 |
| 📎 必须处理 | 包名、模块墙、契约、MVP 顺序、Out 硬墙 |
| 🚀 查缺补漏 | 权限降级、未装 OneIMS、导出失败、深链未知 path |
| ✨ 顺手优化 | 决策台账写回立项文档 |
| 🧠 头脑风暴 | monorepo module vs 新仓；普通权限 vs 特权复用 — 已取前者 |
| 💩 屎山规避 | 不依赖 `:bridge`；不预埋插件平台 |
| ⚠️ 注意事项 | 默认冻结项 2～6 可被用户覆盖；工作树既有 OneIMS 改动勿误伤 |
