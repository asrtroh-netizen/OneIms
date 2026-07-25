# Material Capsule 源码/UI/能力摸底 × OneIMS 功能叠加

> 日期：2026-07-20  
> 样本：`com.pryshedko.mtisland`（Material Capsule v15.5 PREMIUM 本地包）  
> OneIMS：本仓 `com.oneims.app` / `com.oneims.onelink`  
> 方法：DEX 类图 + 字符串/资源键 + Manifest 字符串池 + OneIMS 源码树  
> **更新 2026-07-25**：jadx 1.5.1 已跑；业务可读树见 `.tmp_material_capsule_v155/mt_decompiled_src/`（1434 Java）

---

## 一、Material Capsule · 源码地图

### 1.1 规模

| 项 | 值 |
|---|---|
| 业务类（去重、无 `$` 合成） | ~790 |
| UI 包占比 | **489**（`…ui.*`） |
| DI（Hilt） | 63 |
| model | 69 |
| utils | 52 |
| 广播 `br` | 22 |
| service | 11 |
| widget | 11 |
| billing.data | 6 |
| graph（导航） | 6 |
| ads | 1 |

栈：**Kotlin + Jetpack Compose + Hilt + DataStore + Room + Protobuf 设置模型 + CameraX/ML Kit + Play Billing + Firebase**。

### 1.2 包职责

| 包 | 职责 |
|---|---|
| `…MainAppActivity` / `App` | 入口、Compose NavHost |
| `…graph.screen/dialog/bottomsheet` | 导航图：`Screen` / `DialogScreen` / `BottomSheet` |
| `…service.GlobalActionBarService` | **AccessibilityService**（岛触摸/全局手势壳） |
| `…service.NotificationService` | **NotificationListener**（通知驱动岛内容） |
| `…service.CapsuleServiceViewModel` / `GlobalCapsuleSettings` | 岛运行时状态 |
| `…br.*` | 系统事件：充电/低电/拔电、Wi‑Fi、蓝牙耳机、USB、飞行模式、亮暗/音量、旋转、解锁、应用变更、媒体监听 |
| `…ui.composables.card.capsuleui` | 岛本体绘制（`CapsuleComposables`、挖孔形状、触摸区） |
| `…ui.composables.card.capsules` | 迷你态：充电/时钟/播放器/手电音量/进度/Lottie |
| `…ui.composables.card.cards` | 展开卡片：播放器/快捷方式/滑条/电池/条码/广告/警告… |
| `…ui.composables.app.*` | 设置 App：主屏、卡片库、动态卡、手势、通知、档案、扫码、解锁 Pro |
| `…model.datastore*` | 行为/胶囊/手势/通知/计费/屏幕档案 |
| `…model.room` | 卡片、快捷方式、颜色等持久化 |
| `…billing.data` | Pro 状态机数据类 |
| `…widget` | 系统小部件挑选 |
| `…ads` | 广告位（与「keep ad free」文案对应） |

### 1.3 设置序列化（Protobuf 线索）

根包可见 Proto：`AllGestures` / `AllNotifications` / `AllScreenProfiles` / `GestureProto` / `NotificationSettingsProto` / `MiniCapsuleAlignmentProto` / `CapsuleWidthModeProto` / `ExpandedCapsuleModeProto` 等 → **配置可导入导出/强类型**，不是纯 SharedPreferences 散写。

---

## 二、Material Capsule · UI 结构

### 2.1 导航骨架

```
MainAppActivity
 └─ NavHost
     ├─ Decider（首次/权限决策）
     ├─ MainScreen（主设置壳）
     │    ├─ CapsuleSettings
     │    ├─ Cards / DynamicCards
     │    ├─ Gestures
     │    ├─ Notifications
     │    ├─ ScreenProfiles / ProfileSettings
     │    └─ Settings
     ├─ Add* 流程（Player / Battery / Barcode / Shortcuts / Application…）
     ├─ EditCollapsedCapsule / EditCapsuleMode / Debugging
     ├─ BarcodeScanner
     ├─ CallPermission
     ├─ ChooseWidget / ChooseApplication
     ├─ Dialogs（UnlockPro / Guide / AccessibilityWarning / Theme…）
     └─ BottomSheets（改色 / 岛色 / DayNight 模板）
```

### 2.2 主 UI 模块（按类密度）

| UI 区 | 代表屏/组件 | 用户可见能力 |
|---|---|---|
| 主屏 | `MainScreen` / `CapsuleSettingsScreen` | 总开关、胶囊参数 |
| 卡片库 | `CardsScreen` / `DynamicCardsScreen` | 配置展开岛内容卡 |
| 加卡 | `AddPlayer/Battery/Barcode/Shortcuts/…` | 各类型卡向导 |
| 手势 | `GesturesScreen` / `GestureEditScreen` | 点按/拖动手势 → 动作 |
| 通知 | `NotificationsScreen` / `NotificationEdit` | 哪些通知上岛 |
| 外形 | `EditCollapsedCapsule` / `ChangeIslandColor*` | 折叠形态、颜色、挖孔适配 |
| 多机型 | `ScreenProfilesScreen` | 挖孔/刘海参数档案 |
| 扫码 | `BarcodeScanner` | CameraX + ML Kit |
| Pro | `UnlockProDialog` | 订阅/终身解锁 |
| 系统岛 | `CapsuleComposables` + Accessibility | 真正的「岛」渲染与命中 |

### 2.3 卡片类型（资源键证据）

- `player`（媒体）
- `shortcuts` / `shortcuts_1`
- `slider`（亮度 / 音量 / 手电）
- `charging_indication`
- `barcode`
- `timer` / `loading_indication`
- `volume` / `flashlight`
- `ads`（广告卡）

迷你态组件：`PlayerMinimized` / `ChargingMinimized` / `ClockMinimized` / `FlashlightOrVolumeMinimized` / `ProgressMinimized` 等。

---

## 三、Material Capsule · 能力清单

| # | 能力 | 实现面 | 说明 |
|---|---|---|---|
| 1 | 系统动态岛 | Accessibility + Compose Overlay 形态 | 挖孔对齐、折叠/展开 |
| 2 | 通知上岛 | NotificationListener | 可编辑通知规则 |
| 3 | 媒体控制 | MediaSession + Player 卡 | 播放器迷你/展开 |
| 4 | 充电/低电事件 | BroadcastReceiver | 充电卡/迷你态 |
| 5 | Wi‑Fi / BT / USB / 飞行模式 | 系统广播 | 事件驱动刷新 |
| 6 | 手势动作 | Gestures + Proto | 媒体/自定义动作 |
| 7 | 亮度/音量/手电滑条 | Slider 卡 | 快捷控制 |
| 8 | 快捷方式/联系人/应用 | Shortcuts 卡 | 启动外部 App |
| 9 | 系统小部件嵌入 | AppWidgetHost | WidgetPicker |
| 10 | 条码/二维码 | CameraX + ML Kit | 扫描进卡 |
| 11 | 多屏幕档案 | ScreenProfiles | 适配异形屏 |
| 12 | 主题/岛色/透明度 | BottomSheet 调色 | Day/Night |
| 13 | Pro 订阅/终身 | Play Billing | 功能门闸 |
| 14 | 广告 | ads 模块 | Free 变现；Pro「keep ad free」 |
| 15 | 引导/无障碍警告 | Guide / AccessibilityDialog | 权限教育 |
| 16 | 姊妹产品联动 | `MaterialpodsBroadcastReceiver` + Play 链 | materialpods |

**明确不是**：IMS / CarrierConfig / Shizuku / 无线电特权 —— 与 OneIMS 正交。

---

## 四、OneIMS · 现有功能（源码树采证）

| 域 | 代表模块 | 能力 |
|---|---|---|
| 特权通道 | `PrivilegeBridge` / `OneKuku*` / `ShizukuManager` / `:bridge` | OneKuku 内嵌桥 · Lite=Shizuku |
| 激活 UX | `OneKukuActivationUi` / 通知六位码 / 无线调试 | 配对、激活中态、划掉保活 |
| IMS 写配 | `ImsController` / `CarrierConfigOverrideWriter` / `CarrierProfiles` | VoLTE/VoWiFi/VoNR 等 |
| 诊断 | `DiagnosticsScreen` / `OneClickDiagnosticsManager` / `EpdgChecker` | 能力检测、诊断页 |
| 恢复 | `ReapplyManager` / `OneKukuBootRestore*` / `GuardService` | 开机重放、一键应用上次、恢复系统默认 |
| 双卡 | `DataSimSwitch*` / subId 持久化 | 选卡、防串写 |
| 信号/显示 | `SignalBarSystemStyleManager` / `FiveGSignalReader` / `SystemDisplayOverrideManager` | 信号格、5G 显示增强 |
| 国家/APN | `SimCountryIsoManager` / `ApnCatalog*` / `ImsApnRepairService` | 国家码、APN |
| 快捷入口 | `ImsTileServices` / QS Tile | 快捷设置磁贴 |
| UI | `HomeScreen` / `StatusHero` / `CapabilitiesScreen` / `ExperimentalScreen` / `SettingsScreen` | **应用内**状态卡，非系统岛 |
| 商业 | `DodoPaySupport*` / `SponsorScreen` | 赞助/支付（非 Play Billing 岛逻辑） |

---

## 五、叠加矩阵 · One Capsule 该怎么「加上」

图例：✅ 建议并入 · 🔧 需自研适配 · ⚪ 可后置 · ❌ 不要搬

| Material Capsule 能力 | 叠加到 OneIMS？ | One Capsule 落点 |
|---|---|---|
| 系统岛壳（折叠/展开/挖孔） | ✅ | `:capsule` Overlay + 可选 Accessibility |
| 通知上岛 | 🔧 | **仅**镜像本应用配对码/通道通知，不做通用通知岛 |
| 媒体/充电/Wi‑Fi 通用事件卡 | ❌/⚪ | 偏离 IMS 主线；不做通用 Dynamic Island |
| 手势 → 任意 App | ❌ | 权限面过大 |
| 亮度/音量/手电 | ❌ | 与 IMS 无关 |
| 小部件宿主 | ❌ | 复杂度高、收益低 |
| 扫码 | ❌ | 无业务需求 |
| 屏幕档案/挖孔适配 | 🔧 | P1 做最小挖孔参数（Pixel 机型表） |
| 岛色/主题 | 🔧 | 跟随 OneIMS 品牌色即可 |
| Pro Billing / Ads | ⚪ | 若要商业化自建，不拷其破解包 |
| **通道 READY/激活中** | ✅（OneIMS 独有） | 岛主状态 |
| **选卡 / VoLTE·VoWiFi 摘要** | ✅ | 岛迷你态徽章 |
| **一键应用上次 / 打开配对** | ✅ | 展开态快捷动作 |
| **诊断入口深链** | ✅ | 点岛 → Diagnostics |
| **开机恢复进度** | 🔧 | Guard/BootRestore 事件 → 岛进度条 |
| **QS Tile** | ⚪ | 已有磁贴，与岛并存 |

### 推荐产品定义（落地口径）

> **One Capsule** = OneIMS 的「系统级状态岛」：  
> 显示通道/SIM/IMS 摘要，展开提供 OneIMS 快捷动作；  
> **不**做成 Material Capsule 的通用通知娱乐岛。

---

## 六、建议信息架构（UI）

```
[迷你岛]
  绿点 = 通道 READY | 黄 = ACTIVATING | 红 = ERROR
  文案 = OneKuku/OneLink · SIM1/SIM2 · VoLTE●

[展开岛]
  · 应用上次配置
  · 打开配对 / 打开 Shizuku
  · 诊断
  · 进入 OneIMS 首页
```

与现有 `StatusHero` 状态机对齐（Inactive / Activating / Ready），避免两套语义。

---

## 七、证据与缺口

| 项 | 状态 |
|---|---|
| 包级类图 / UI 树 / 能力表 | ✅ 本报告 |
| Manifest 组件/权限 | ✅ 上一轮报告 |
| 改包（Liteapks/SignatureKiller） | ✅ 已定性，禁止进仓 |
| jadx 方法级伪代码 | ❌ NOT RUN（下载过慢） |
| 动态跑岛看帧动画 | ❌ NOT RUN |

产物路径：

- `docs/architecture/2026-07-20-one-capsule-integration.md`（前序方案）
- `.tmp_material_capsule_v155/analysis/source_map.json`
- 本文件：`docs/architecture/2026-07-20-mc-source-ui-capability-x-oneims.md`

---

## 八、下一步（执行序）

1. **拍板范围**：只做 IMS 状态岛（推荐）还是还要通用通知卡（不推荐）。  
2. **P0**：`StatusHero` 与迷你岛视觉对齐（无 Overlay）。  
3. **P1**：`:capsule` 模块 + Overlay + `CapsuleEvent` 契约接 `PrivilegeBridge`/`GuardService`。  
4. 可选：装好 jadx 后只反编译 `service` + `capsuleui` + `billing` 补方法级细节。
