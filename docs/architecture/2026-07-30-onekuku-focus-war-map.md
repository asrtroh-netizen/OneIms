# 2026-07-30 · OneKuku 专攻作战图

**类型**：架构聚焦 / 工作边界（非发版指令）  
**规模**：M · 本文件是「专攻 OneKuku」会话的单一作战入口  
**flavor**：`onekuku` · `applicationId=com.oneims.app` · `CHANNEL_USES_EMBEDDED_BRIDGE=true`

---

## 0. 专攻声明（本会话硬边界）

| 项 | 定调 |
|---|---|
| **主战场** | OneKuku 内置通道：配对 → ADB → `onebridge_server` → Binder → Hero/恢复 |
| **非主战场** | OneLink 轻量壳、OneTools 胶囊、国产 OEM 大面积兼容（除非直接挡 OneKuku 通道） |
| **通信业务** | Pixel VoWIFI/VoLTE 仍是产品总优先级（见 `2026-07-30-product-priority-pixel-first.md`）；本专攻不削弱 P0，只是把**通道工程注意力**收到 OneKuku |
| **发版** | 仍遵守双线同更 SOP；专攻 ≠ 只发 OneKuku 单包 |
| **立项闸门** | 不多 App 授权商店、不全量 `IShizukuService`、不做换皮第二 Shizuku |

---

## 1. 架构一句话

```
OneIMS(onekuku) ──无线调试/ADB──► onebridge_server(app_process)
                              ──Binder──► BridgeBinderProvider / Client
                              ──► IMS 恢复 / 快捷设置 / 业务
```

对照官方：`V15 Manager ──ADB──► shizuku_server ──Binder──► 多 App`  
原理同构；**协议面刻意更窄，开机/复连韧性已向 V15 对齐大半。**

---

## 2. 模块地图（改代码时先认门牌）

| 层 | 路径 / 入口 | 职责 |
|---|---|---|
| Flavor 装配 | `app/build.gradle.kts` `onekuku` | `:bridge` + libadb 族仅 onekuku |
| 通道探测/拉起 | `app/src/onekuku/.../OneKukuCoreComponent` | Phase4 宿主内嵌 starter，不再装独立桥 APK |
| 无线激活 | `OneKukuEmbeddedAdbActivator` / `OneKukuAdbMdns` / `OneKukuMiniAdbClient` | pair → connect → start → binder 门禁 |
| 环境 | `OneKukuAdbEnvironment` | `/proc/net/tcp*` 端口发现 + last-port |
| 配对 UX | `OneKukuPairingHostService` / `OneKukuPairingNotification` / `WirelessPairingCodeReceiver` | RemoteInput + specialUse FGS |
| 特权桥客户端 | `privilege/OneBridgePrivilegeBridge` + `OneBridgeProtocol` | 与 `:bridge` 协议 |
| 桥实现 | `bridge/src/.../BridgeService` 等 | `onebridge_server` 侧 |
| 开机韧性 | `OneKukuWifiReadyMonitor` / `UserPresentRestartReceiver` / `WatchdogService` / BootRestore* | 对齐 V15 冷启/解锁/死 binder |
| 前台复连 | `MainActivity.schedulePrivilegeReconnectShots` | 0/5/15s；先等 binder 再 ADB |
| UI 状态 | `OneKukuCardState` / Hero 三态 | READY 盖过脏 CONNECTING |
| 共用业务 | `app/src/main/.../onekuku/*` Restore / Snapshot / Sleep | 双线复用；改时注意 onelink 桩 |

---

## 3. 已落地基线（当作「不要重做」）

### 3.1 V15 开机韧性（2026-07-29）

见 `docs/changes/2026-07-29-onekuku-v15-boot-alignment.md`：

- 端口发现 + last-port、`WifiReadyMonitor`、`USER_PRESENT` 0/5/15s  
- Watchdog（可配）、自动六位码（默认关）、tcpip 可配  

> 旧矩阵 `2026-07-29-onekuku-vs-shizuku-v15-alignment.md` §2 里部分「无」已过时，以 boot-alignment + 本图为准。

### 3.2 今日（2026-07-30）复连 / 假激活

| 提交主题 | 文档 | 解决什么 |
|---|---|---|
| V15 式 0/5/15 特权复连 | `onekuku-v15-reconnect-shots` | 划掉后假死 / 假未激活 |
| READY 盖过 CONNECTING | `onekuku-ready-beats-connecting` | ACTIVE 却显示激活中横跳 |
| 复连先等 binder | `onekuku-reconnect-no-adb-toast` | 少碰 ADB，减「USB 调试」弹窗刷屏 |
| 相关 | PID binder 重投、SIGHUP 脱离等 | 见近期 `fix(onekuku)` / `fix(onebridge)` 提交 |

---

## 4. 建议下一刀（专攻 backlog）

按 **用户痛感 × 架构收益 × 风险** 排序；**未点名不自动开刀**。

| 优先级 | 候选 | 为何 | 风险 | 建议验证 |
|---|---|---|---|---|
| **P0** | 真机矩阵验收今日复连三连 | 代码已合，真机多为 NOT RUN | 低（只测） | 划掉/force-stop/断 binder；log 含 `wait-binder-only` / 无 CONNECTING 横跳 |
| **P0** | 冷启全链路抽检 | 开机韧性声称已齐，缺证据 | 中 | 冷启 → Wi‑Fi → 解锁 → READY → 配置重放 |
| **P1** | 复连路径再收敛 | lite 仍更稳时做差分 | 中 | 对照外置 V15.1.0：binder 丢失后秒醒率 |
| **P1** | Hero/相位单一真源整理 | 防再次「相位残留」 | 中 | 单测 + 前台复连脚本 |
| **P2** | Watchdog / 自动填码默认策略复盘 | 便利 vs 权限摩擦 | 低 | 设置项文案与默认值 |
| **P2** | 对齐矩阵文档刷新 | 避免后人按过时「无」开工 | 低 | 文档 diff |
| **不做** | 完整 Shizuku API / 多 App / 独立 Manager | 立项闸门 | — | — |

---

## 5. 改动铁律（专攻期）

1. **先认 flavor**：改 `src/onekuku` / `:bridge`；勿把重逻辑塞进 onelink 桩。  
2. **binder 就绪才算 Success**；UI 不得用脏 phase 盖过 `serviceReady`。  
3. **前台复连优先 wake + 等 binder**，ADB 是回落不是默认连打。  
4. **退后台不拉 App 轮询 FGS、不杀桥**（见 sleep-vs-power）。  
5. **验证**：能编 `compileOnekuku*Kotlin` 就编；声称行为必须以真机/log 或显式 NOT RUN。

---

## 6. 证据索引

- 双线设计：`docs/design/2026-07-16-dual-channel-onekuku-onelink.md`  
- V15 对齐全景：`docs/architecture/2026-07-29-onekuku-vs-shizuku-v15-alignment.md`  
- 开机对齐变更：`docs/changes/2026-07-29-onekuku-v15-boot-alignment.md`  
- 休眠边界：`docs/architecture/2026-07-29-onekuku-sleep-vs-power.md`  
- 产品总优先级：`docs/architecture/2026-07-30-product-priority-pixel-first.md`  
- 源码：`app/src/onekuku/**`、`bridge/**`、`app/src/main/java/com/oneims/app/onekuku/**`
