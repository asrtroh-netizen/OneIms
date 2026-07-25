# One Capsule × Material Capsule · 结合架构方案

> 日期：2026-07-20  
> 输入：OneIMS（本仓）+ Material Capsule v15.5 静态逆向结论  
> 模式：规划交付（本轮不落业务代码，除非后续明确开工）  
> **学习材料 2026-07-25**：UI 专用学习包 `.tmp_material_capsule_v155/mt_ui_study_kit/`（含路线图与 clean-room 清单）

---

## 1. 需求澄清

| 项 | 内容 |
|---|---|
| 表层 | 把 OneIMS 项目与 Material Capsule「结合」成 **One Capsule** |
| 深层 | 用「系统级胶囊/动态岛」交互壳，承载 OneIMS 的 IMS/通道状态与快捷动作，形成差异化产品形态 |
| 非目标 | ❌ 嵌入/重打包 Liteapks 改包样本；❌ 复用其 SignatureKiller / 破解 Billing；❌ 把 OneIMS 做成通用 Dynamic Island 全家桶 |

---

## 2. 两边事实对照

### OneIMS（本仓已有）

| 维度 | 现状 |
|---|---|
| 产品 | Pixel IMS 配置 / 诊断 / 恢复 |
| 通道 | OneKuku 内嵌 Bridge · OneLink/Shizuku |
| UI | Compose · `StatusHero` · 选卡 Pill（旧名 SimCapsule） |
| 运行时 | `GuardService`、特权 binder、开机自启 |
| 模块 | `:app` + `:bridge` |
| 缺口 | **无** 系统级悬浮胶囊 / Accessibility 岛 / 通知镜像岛 |

### Material Capsule（逆向样本）

| 维度 | 现状 |
|---|---|
| 产品 | 通用 Dynamic Island / 胶囊 UI |
| 壳 | Accessibility + NotificationListener + Compose 动画 |
| 内容 | 媒体/充电/手势/卡片/小部件… |
| 付费 | Play Billing + UnlockPro 门闸 |
| 风险 | 本样本为 **改包**（Liteapks + SignatureKiller），不可当依赖源 |

---

## 3. 方案矩阵

| 方案 | 做法 | 收益 | 成本/风险 | 建议 |
|---|---|---|---|---|
| **A. 壳内胶囊化** | 仅改首页：`StatusHero` → 胶囊视觉 + 手势折叠 | 快、无新权限 | 不像「系统岛」 | 作过渡 |
| **B. One Capsule 系统岛（推荐）** | 新建 `:capsule`：Overlay/可选 Accessibility，**只播 OneIMS 领域事件** | 产品差异清晰、边界干净 | 中等：权限 UX、各厂商杀后台 | ✅ 主路径 |
| **C. 通用岛 + IMS 插件** | 仿 Material Capsule 全量卡片生态 | 想象力大 | 高：爆炸半径、与 OneIMS 主线抢焦点 | ❌ 不做 |

**选定：B（可含 A 的视觉预演）**

---

## 4. 目标架构（B）

```
┌─────────────────────────────────────────────┐
│ :app  OneIMS 主业务                         │
│  IMS / CarrierConfig / 诊断 / 恢复           │
│  PrivilegeBridge (OneKuku | Shizuku)        │
└───────────────┬─────────────────────────────┘
                │ 领域事件总线（单向）
                ▼
┌─────────────────────────────────────────────┐
│ :capsule  One Capsule 展示壳（新建）          │
│  CapsuleController                          │
│  ├─ StateMapper: OneImsState → CapsuleModel │
│  ├─ OverlayRenderer (Compose in overlay)    │
│  ├─ ActionRouter → 回跳 :app 深链/广播       │
│  └─ (可选) AccessibilityHit / Notif mirror  │
└─────────────────────────────────────────────┘
                │
                ▼
        系统顶栏「岛」：通道态 / 选卡 / VoLTE / 配对码提示
```

### 契约（跨模块真源）

建议新增轻量契约（Kotlin data / sealed），**禁止 capsule 反查 CarrierConfig**：

```text
CapsuleEvent
  ChannelState { INACTIVE | ACTIVATING | READY | ERROR }
  SimFocus { slot, subId, label }
  ImsBadge { volte, vowifi, vonr }   // 可读摘要，非完整诊断
  QuickAction { OPEN_HOME | APPLY_LAST | PAIR | RESTORE_DEFAULT }
```

ID/时间戳一律字符串或明确毫秒；动作幂等（重复点「应用上次」不炸）。

### 依赖方向（铁律）

- `:capsule` → 只依赖契约 / 公共 UI token  
- `:app` → 实现事件生产 + 动作执行  
- **禁止** `:capsule` → `:bridge` 直接特权调用（特权只留 PrivilegeBridge）

---

## 5. 从 Material Capsule「可学 / 不可学」

| 可学（交互模式） | 不可学（直接搬） |
|---|---|
| 迷你岛 ↔ 展开岛状态机 | 其 Billing / UnlockPro / Liteapks 水印 |
| 手势展开、通知驱动刷新的节奏 | 改包签名绕过链路 |
| 设置项「Pro 门闸」产品结构（若自研付费） | 其源码/资源直接拷贝进仓 |
| Accessibility 仅作命中扩展的思路 | 无障碍全家桶滥用 |

---

## 6. 分期落地（RIPER 执行切片）

### P0 · 视觉预演（S，约 1–2 天）

- 首页 `StatusHero` 胶囊化（圆角、折叠、选卡并入岛）
- 不申请 Overlay；验证文案与状态映射

### P1 · 系统岛 MVP（M，约 1 周）

- 新建 `:capsule` + Overlay 权限引导  
- 订阅 `ChannelState` / `SimFocus`  
- 点击岛 → 深链回 OneIMS  
- 双产品线（OneKuku / Lite）共用同一 capsule 模块

### P2 · 动作岛（M）

- 展开态快捷：应用上次配置 / 打开配对 / 查看诊断摘要  
- 与 `GuardService` 联动：通道 binder 到达时岛变绿

### P3 · 可选增强（按需）

- NotificationListener：仅镜像 **本应用** 配对六位码通知（不做通用通知岛）  
- 付费：若需要，自建 Play Billing（对齐 OneIMS 产品策略），不碰样本破解逻辑

---

## 7. 风险与门禁

1. **厂商后台限制**：岛进程要跟现有 FGS/Guard 策略对齐（参考 dontkillmyapp 心智，但实现走 OneIMS 已有自启）。  
2. **权限可信**：Overlay / 无障碍必须说明「只为 OneIMS 状态岛」，默认关。  
3. **供应链**：禁止把 Material Capsule PREMIUM 样本当依赖或资源源。  
4. **范围失控**：任何「通用媒体岛/充电岛」需求 → 新开产品，不塞进 OneIMS 主线。

---

## 8. 成功标准

- [ ] 用户一眼能从系统岛看到：通道是否 READY、当前卡、VoLTE 等关键摘要  
- [ ] 岛上动作能回到 OneIMS 完成真实特权操作（有证据：日志 / 配置写回）  
- [ ] `:capsule` 可单独开关；关闭后 OneIMS 核心功能完整  
- [ ] 无引入第三方改包代码 / 签名杀手  

---

## 9. 本轮结论

**One Capsule = OneIMS 领域状态的系统胶囊壳**，不是 Material Capsule 的二次打包。  
Material Capsule 只作 **交互参照**；实现必须自研、挂在现有 Privilege / Guard 体系上。

下一步需哥哥拍板：**先做 P0 壳内预演，还是直接开 P1 `:capsule` 模块脚手架。**
