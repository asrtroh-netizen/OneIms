# 2026-07-30 · V15.1.0 可删清单（撤旧通道前的减法）

**类型**：架构盘点 / 裁剪候选（本拍只列表，不删代码）  
**对象仓**：`E:\GQ\One\_forks\ShizukuDropIn-Local` · 身份 `V15.1.0` / `151000`  
**对照**：`HSSkyBoy-Shizuku-clean`（V15.0.0 瘦身上限）· OneIMS 刚需见 `docs/design/2026-07-15-onebridge-privilege-min.md` §2  
**战略语境**：用户方向「撤掉原来的特权通道」（OneKuku 内嵌 **OneBridge**）→ 先把 V15.1.0 拆开看能砍什么，再谈替换落地。

---

## 0. 一句话

V15.1.0 = **Plus 大底座 + V15 皮 + 冷启精华**。对 OneIMS/OneKuku 而言，**一大半 Plus 能力面可以物理删或永久关**；必留的是 **官方兼容包名 + binder 服务 + 无线/ADB 自启 + 授权 + 冷启韧性**。

若目标是「只服务 OneIMS」，裁剪上限应逼近 **V15.0.0 clean**（甚至更薄），而不是保留 oem4 白名单全家桶。

---

## 1. 必留（删了 OneIMS 会断）

| 块 | 路径/能力 | 为何 |
|---|---|---|
| Drop-In 包名 | `moe.shizuku.privileged.api` + Provider | App 侧 `rikka.shizuku` / 授权契约 |
| Server 核心 | `server/` · `IShizukuService` 最小面 | `ping` / 授权 / binder 上下线 |
| 客户端 API | `api` submodule 兼容面 | `SystemApiBroker` 要的 shell binder 包装 |
| 无线调试拉起 | `manager/.../adb` · pair/connect/start | 无电脑急救 |
| 冷启韧性 | BootComplete / UserPresent 0·5·15 / WifiReady / SelfStarter / WirelessBootStartWorker / Watchdog | 划掉/重启后秒醒（今日 OneKuku 假死对照的参照系） |
| 首页最小壳 | V15 LibrarySkin：Hero + 无线启动 + 授权入口 | 用户能完成「启动→授权」 |
| 设置最小集 | 开机自启 / Watchdog / TCP 口 / 主题语言（可选） | 运维必要 |

**OneIMS 刚需服务名**（经 binder）：`activity` · `carrier_config` · `isub` · `phone`（见 OneBridge 立项 §2.2）。不需要完整「任意 system service 透传商店」。

---

## 2. 强烈建议去掉（对 IMS 无硬依赖 · 优先砍）

> 「去掉」= 源码/模块级删除或永不编进产物，不只是默认关。默认关仍占体积与攻击面。

### 2.1 Plus 实验 / 幽灵 / 刷机类（默认已关 · 应物理删）

| 能力 | Settings Key | 模块线索 |
|---|---|---|
| Dhizuku | `dhizuku_mode` | `admin` / 相关 |
| AVF Manager | `avf_manager_enabled` | Plus |
| Storage Proxy | `storage_proxy_enabled` | Plus |
| Activity Manager Plus | `activity_manager_plus_enabled` | Plus |
| NPU / 实验 AI / Master AI | `npu_*` / `ai_core_master_*` / `ai_core_experimental_*` | Plus |
| Binder Firewall / Logging / Shadow | `binder_firewall_*` / `shadow_binder_*` | `security` |
| Root Magisk/Busybox/iptables mocking、ghosting、bootloader flash | `root_*_ghosting_*` / `bootloader_*` | Plus root 实验 |
| Overlay FS Proxy / Software Keystore fallback | 对应 KEY | 边缘 |
| Native Window Crawler / Vector | 对应 KEY | 边缘 |
| On-device ADB TCP（非 Force WADB） | `on_device_adb_tcp` | 与主路径重复风险 |

### 2.2 oem4 白名单默认开、但对 OneIMS 可砍（高价值减法）

| 能力 | Key（默认 ON） | 砍的理由 |
|---|---|---|
| AICore+ | `ai_core_plus_enabled` | IMS 写入不用 |
| Device Spoofing | `spoof_device_enabled` | 与通话/IMS 无关；合规风险 |
| Window Manager Plus | `window_manager_plus_enabled` | 非刚需 |
| Overlay Manager Plus | `overlay_manager_plus_enabled` | 非刚需 |
| Network/DNS Governor | `network_governor_plus_enabled` | 非刚需 |
| Continuity Bridge | `continuity_bridge_enabled` | 非刚需 |
| Custom API 总开关全家桶 | `custom_api_enabled` | 除非有明确 App 调用清单，否则整面可关后删 |
| Shell Interceptor | `shell_interceptor_enabled` | OneIMS 不走这层 |
| Local ADB Proxy (15555) | `adb_proxy_enabled` | 户外急救用内嵌/官方无线路径即可 |
| SU Bridge | `su_bridge_enabled` | 非 Root 主路径；有 Root 再议 |
| Root Compatibility Hub | `experimental_root_compat` | 非 Pixel/主路径刚需 |
| Samsung System UID 1000 | `samsung_system_uid_escalation_enabled` | 非全机型；可按 OEM 另开 |
| 自动化首页卡 | `show_automation_home`（默认 true） | 对 IMS 噪音；模块 `automation`(~10 文件) 可整删 |
| Service Doctor | Activity 入口 | 排障用，可外置/隐藏，不必进默认产物 |
| QS Tile（Manager 内） | TileService | OneIMS 已有自己的磁贴；Manager Tile 可砍 |
| 应用内更新 / OTA / plugin | `ota` / `update` / `plugin` | 自有发版链路；减攻击面 |
| 活动日志 / 终端 / 了解更多 | 已默认隐藏 | **直接删模块** `activitylog` / 终端相关，别留尸 |

### 2.3 Manager 包级候选（按目录体量）

| 包目录 | 约文件数 | 建议 |
|---|---|---|
| `automation` | 10 | **删** |
| `activitylog` | 2 | **删** |
| `plugin` | 2 | **删** |
| `ota` / `update` | 1+3 | **删或极瘦**（保留「检查更新」外链即可） |
| `scripting` | 3 | **删**（非 IMS） |
| `health`（Service Doctor） | 1 | **可删或降为调试 flavor** |
| `onboarding` / `migration` | 1+1 | **可删**（换通道期另做引导文案） |
| `widget` | 6 | **可删**（OneIMS 不依赖 Manager 小组件） |
| `backup` | 2 | 慎：设置备份有用；可留极简 |
| `di` | 1 | 随 Plus 面收缩再砍 |
| `security` | 2 | Firewall/Shadow 走后可大幅瘦 |
| `shell`（Manager 终端） | 4 | **删 UI**；勿碰 server shell |
| `home` | 32 | **留皮、删卡**：只留 Hero/无线/授权/开机 |
| `settings` | 25 | **大砍 Plus 页**，留冷启/主题 |
| `adb` + `starter` + `receiver` + `worker` | 16+3+14+3 | **必留**（冷启热路径） |
| `authorization` / `app` / `management` | 小 | **必留**（授权列表可极简） |

---

## 3. 慎删（先关默认，确认无调用再物理删）

| 项 | 原因 |
|---|---|
| Force Start WADB | 小米/OEM 无线调试难拉时有用；可留开关、勿先删实现 |
| Watchdog | 假死对照刚需；默认开合理 |
| Drop-In 兼容层（官方包名） | 撤 OneBridge 后这是主契约 |
| `legacy` 配对 | 旧机可能需要；默认关即可 |
| `installer` | 若仍引导装通道 APK 则留 |
| Root `su -c` 兜底 | 有 Root 用户的冷启加速；体积小 |
| 批量授权 UI | OneIMS 单 App 用不上，但调试方便；可隐藏 |
| Plus `af.shizuku.*` 命名空间 | 与运行时包名分离；整仓迁回 `moe.shizuku.*` 成本高，**裁功能不急改包名** |

---

## 4. 与「撤掉原来的特权通道」的衔接

| 现状（OneKuku） | 目标方向（本清单假设） |
|---|---|
| 内嵌 OneBridge + libadb + 自研配对 FGS | **撤 OneBridge 实现面**，改依赖瘦身后的外置/同机 V15.1.0（或再内嵌其最小 server） |
| `CHANNEL_USES_EMBEDDED_BRIDGE=true` | 需另案：改 bootstrap → Shizuku API（类似 OneLink）或内嵌瘦 server |
| OneBridge 立项「不要完整 Shizuku」 | **不矛盾**：用的是 **裁过的 V15.1.0**，不是 Plus 全家桶 |

**建议落地顺序（尚未执行）**

1. 在 DropIn 仓做 **strip flavor**（本清单 §2 全砍）→ 出「V15.1.0-slim」APK  
2. OneIMS `onekuku` 线改为走官方 Shizuku 客户端契约（对齐 OneLink），验证 IMS 写入  
3. 再拆 `:bridge` / `src/onekuku` 内嵌 ADB 是否仍要保留「无电脑拉起」（可保留激活器、只换 start 目标）  
4. 最后才删 OneBridge 源码与依赖  

---

## 5. 裁剪目标体量（经验锚点）

| 产物 | 源文件体量（既有对比） | 含义 |
|---|---|---|
| V15.0.0 clean | ~85 文件 / ~0.4MB 源 | 理想上限（Pixel 已稳） |
| V15.1.0 DropIn 现状 | ~213 文件 / ~1.36MB 源 | 待砍 |
| 本清单执行后预期 | 逼近 clean + 少量 OEM 冷启补丁 | 仍大于 OneBridge，但远小于 Plus |

---

## 6. 验证（本拍）

| 检查 | 结果 |
|---|---|
| 仓路径 / version 身份 | PASS（既有文档 + `README.LOCAL.md`） |
| Settings Key 默认值抽样 | PASS（`ShizukuSettings.java`） |
| Manager 分包文件数 | PASS（本轮目录计数） |
| 实际删除 / 编包 / 真机 | **NOT RUN**（用户只要列表） |

---

## 7. 请你拍板的下一刀

1. **A**：按 §2 做 `dropinSlim` flavor（物理删）  
2. **B**：先只改默认全关（软裁，快）  
3. **C**：直接对照 V15.0.0 clean，DropIn 只保留 oem1/oem6 冷启差分  
4. **D**：确认「撤旧通道」= 撤 OneBridge；开始 OneIMS `onekuku` 改挂 Shizuku 客户端的改造设计  
