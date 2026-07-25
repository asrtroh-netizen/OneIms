# 2026-07-25 · 「南宫支持 Android 17」声称 vs OneIMS 现状

**规模**：M（认知对齐 + 方案取舍；默认不改主写入路径）  
**状态**：分析完成 · **已落地**探测进诊断 +「强制临时写入」实验开关（见 `docs/changes/2026-07-25-platform-persist-probe-force-temporary.md`）  
**附件**：Gemini 对话截图（讲 `cmd phone cc set-value` 的 `-p` / `isSystemApp()`，非南宫 changelog）

---

## 1. 结论（先给答案）

| 问题 | 答案 |
|---|---|
| 我们是不是弄错了「不支持 17」？ | **没有。** OneIMS 未对 Android 17 做硬性拒写；README / Broker 已含 A17 委托清理兼容。 |
| 南宫说「支持 17.0」是否推翻我们？ | **通常否。** 「App 能在 A17 上写 CarrierConfig」≠「非 system 身份拿到真正 `persistent=true`」。 |
| 附图在说什么？ | 普通工具只改内存态；`-p` 才写持久库；系统对 `-p` 走 `isSystemApp()`——与本仓既有铁律一致。 |
| 要不要先做单独按钮/开关？ | **可以，但必须先定语义。** 实验页已有「Root 增强」；不宜再造一个含义模糊的「南宫兼容开关」。 |

---

## 2. 证据

### 2.1 附图（用户附件）

- 主题：Carrier Config `-p`（Persistent）与权限校验陷阱
- 机制：带 `-p` 时 `CarrierConfigLoader` 侧校验 `isSystemApp()`
- **未出现**「南宫官方支持矩阵 / changelog 写支持 17.0」原文

### 2.2 OneIMS 仓内

| 事实 | 指针 |
|---|---|
| 对外宣称 Android 16/17 | `README.md` 标题行与 §Android 16/17 |
| A17 委托清理兼容 | `docs/changes/2026-07-13-android17-stop-delegate-compat.md`；`SystemApiBroker` / `ShellDelegateCleanupPolicy` |
| 写入：先 persistent，被拒回退 temporary | `CarrierConfigOverrideWriter.overrideConfigBestEffort` |
| 开机重放兜底 | `BootReceiver` → `OneKukuBootRestoreService` / `ReapplyManager` / `GuardService` |
| 实验开关「Root 增强」已存在 | `ExperimentalScreen` + `RootPersistenceSupport` + `ConfigStore.isRootPersistEnhance` |
| 兼容体检下限是 API 31+，**无 A17 黑名单** | `CompatChecker`（`SDK_INT >= 31`） |
| 仓库无「南宫」产品字符串 | 全仓检索无命中；社区语境≈ vvb2060/Ims 一类 |

### 2.3 口径漂移（小问题，不是「不支持 17」）

- `README.md` 仍写「CarrierConfig 覆盖默认 `persistent=false`」
- 代码铁律是「优先 `persistent=true`，被拒再 temporary」
- **建议**：后续改 README 与实现对齐（独立小改，不绑本决策）

---

## 3. 概念拆开（避免再被社区话术带偏）

```
「支持 Android 17」可能指三件完全不同的事：

A. App 能在 API 37 上启动、委托、写入不因 stopDelegate 误失败
B. 写入后 VoLTE 等能力当场可用（多为 temporary）
C. 重启后不依赖 Wi‑Fi/通道重放仍保留 CarrierConfig（真 persistent / system 身份）

OneIMS 目标覆盖 A + B（+ Boot/Guard 重放逼近 C 的体验）
南宫「支持 17」话术多半停在 A/B；附图反而证明 C 被 isSystemApp 卡住
```

---

## 4. 方案矩阵（若要「单独开关」）

| 方案 | 做什么 | 收益 | 风险 | 建议 |
|---|---|---|---|---|
| **0. 不做新开关** | 只修正 README 口径 + 诊断文案强调「系统持久 vs 临时+重放」 | 零侵入 | 用户仍可能被社区话术误导 | **默认推荐**（先澄清） |
| **1. 增强现有「Root 增强」** | 文案写清：A17 上仍可能 temporary；开关只增强可观测/Root 旁路 | 复用入口 | 不改变平台锁 | 有 Root 用户时优先 |
| **2. 新增「强制临时写入」实验开关** | 跳过 try-persistent，直接 temporary（减拒写/拖 phone 风险） | 部分 A17 机更稳 | 语义难懂；需真机矩阵 | 仅当真机证实 try-persistent 有害时 |
| **3. 新增「南宫模式/兼容模式」大开关** | 笼统改写主路径 | 营销感 | 屎山/预期失控；**否决** | 不做 |
| **4. Magisk/system 化真 persistent** | 身份升到 system | 真正逼近 C | 分轨维护、签名、与免 Root 主线冲突 | L 级另立项 |

**架构推荐**：先 **方案 0**；若哥哥要可点击的实验入口，选 **方案 1 或 2**（互斥语义，写进开关副文案），绝不做方案 3。

---

## 5. 明确非目标

- 不整包抄南宫 / 不承诺破解 `isSystemApp`
- 不把「能跑 A17」写成「重启永不丢配置」
- 不在未选型时改 `CarrierConfigOverrideWriter` 主路径

---

## 6. 验收（若落地开关）

| 场景 | 期望 |
|---|---|
| 开关关 | 与今日 3.0.2 行为一致 |
| 方案 2 开 | 写入结果明确 temporary；重启依赖既有重放 |
| A17 真机 | 委托清理失败不冒充业务失败；诊断可见持久模式 |
| 免 Root | 不出现「已永久写入」假成功文案 |

---

## 7. 截图 ↔ vvb2060/Ims（南宫系）源码逐点对照（2026-07-25 补）

**源码树**：`.tmp_vvb2060_ims`（`versionName=3.1` / `versionCode=6`）  
**附图论点**：内存态会丢；`-p`/persistent 写持久库；带持久时系统校验 `isSystemApp()`。

| 附图说法 | 对方源码落点 | 含义 |
|---|---|---|
| 普通改 CarrierConfig 重启丢 | `PrivilegedProcess.onCreate` 非沙盒分支：`overrideConfig(context, **false**)` | 默认 Instrumentation 路径只写 **临时** |
| `-p` / persistent 写持久库 | 沙盒分支：`overrideConfig(context, **true**)` | 仅走 SDK Sandbox + shell 委托时才尝试真持久 |
| 系统校验 `isSystemApp()` | `ShizukuProvider.canPersistent()`：`CarrierConfigLoader.getDeclaredMethod("isSystemApp")` | **不是绕过**，是 **探测** 平台有没有这道门 |
| 有门则难持久 | `isSystemApp` 存在后，再探 `isSdkSandboxUidInternal`：存在则 `canPersistent→false` | 新平台（含多数 A16 QPR2+/A17）→ **直接放弃 persistent 尝试**，改 `INSTR_FLAG_NO_RESTART` + temporary |
| 持久被拒要回退 | `overrideConfig`：`SecurityException` 且原 `persistent==true` → 再调一次 `persistent=false` | 与 OneIMS `CarrierConfigOverrideWriter.overrideConfigBestEffort` **同构** |

### `canPersistent` 决策树（源码语义）

```
反射 CarrierConfigLoader
├─ 无 isSystemApp 方法     → true（老系统，可试 persistent + sandbox）
├─ 有 isSystemApp
│   ├─ 有 isSdkSandboxUidInternal → false（新系统锁死 sandbox 持久）
│   └─ 无该内部方法           → true（仍试 sandbox 持久）
└─ 其它异常                 → false
```

### 对「南宫支持 17.0」的源码级裁决

1. **3.1 能在新系统上跑**，是因为它在探测到 `isSystemApp`+`isSdkSandboxUidInternal` 后 **主动改写 temporary**，不是魔法破解 `-p`。  
2. Gemini 附图描述的正是对方 `canPersistent` / `overrideConfig` 所面对的 **同一道 AOSP 门**。  
3. OneIMS 3.0.2 的 try-persistent→temporary + Boot/Guard 重放，与对方策略 **同族**；我们并未「弄错支持矩阵」，差别在产品层（重放/诊断/双包），不在「谁偷偷拿到了 system 身份」。
