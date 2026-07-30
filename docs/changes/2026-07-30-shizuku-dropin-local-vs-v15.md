# ShizukuDropIn-Local（oem1～oem4）vs V15 全盘比较

**日期**：2026-07-30  
**对照仓**：

| 线 | 路径 | HEAD（本轮采证） |
|---|---|---|
| Pixel / V15 主线 | `E:\GQ\One\_forks\HSSkyBoy-Shizuku-clean` | `1ba7389` |
| 小米 / Drop-In 融合 | `E:\GQ\One\_forks\ShizukuDropIn-Local` | oem7：`5fb9425`，对外 `V15.1.0` / 151000 |

**策略前提（既定）**：Pixel 继续用 V15；小米用 DropIn-Local；不推 thejaustin 远端；不改 V15 仓做本线融合。

---

## 1. 一句话结论

| 维度 | 结论 |
|---|---|
| 关系 | **不是同一产品线的小改**，而是「Plus Drop-In 底座 + V15 整套首页皮 + V15 冷启精华 + 白名单默认」 |
| 包名（给 App 用） | 两边默认都是 `moe.shizuku.privileged.api` → **不能同机并存** |
| 对用户可见首页 | 都是 V15 LibrarySkin（Hero / 无线 / 2×2 / 开机卡） |
| 对 IMS 有用的增量 | DropIn 多：Service Doctor、Force WADB、QS Tile、SU Bridge、ADB Proxy、Shell Interceptor、一批 Plus API |
| V15 仍更强的冷启热路径 | `SelfStarterService` 直拉 FGS + `WirelessBootStartWorker`（DropIn 尚未整包迁入，仍以 `AdbStartWorker` 为主） |

---

## 2. 身份与构建

| 项 | V15（clean） | DropIn-Local +oem4 |
|---|---|---|
| 版本名 | `V15.0.0` | **`V15.1.0`**（oem5 起；不再暴露 Plus/r2185/+oem） |
| versionCode | `150000` | **`151000`** |
| 默认 applicationId | `moe.shizuku.privileged.api` | **dropin** flavor：`moe.shizuku.privileged.api`（`isDefault=true`） |
| 可选另一包名 | 无 | `shizukuplus` → `af.shizuku.plus.api` |
| Manager namespace | `moe.shizuku.manager` | `af.shizuku.manager` |
| Server namespace | `moe.shizuku.server` | `af.shizuku.server` |
| Provider authority | `${applicationId}.shizuku` | dropin 另含 `moe.shizuku.privileged.api` 兼容（`tools:replace`） |
| mgr+srv 源文件体量 | ~85 文件 / ~0.4MB | ~213 文件 / ~1.36MB（Plus 能力面大很多） |

---

## 3. 架构与入口

```text
V15:     MainActivity → HomeActivity → HomeComposeScreen → LibrarySkinHomeBody
DropIn:  MainActivity → V15SkinHomeActivity → HomeComposeScreen → LibrarySkinHomeBody
         （Plus 启动/授权/服务栈仍在 af.shizuku.*）
```

| 项 | V15 | DropIn-Local |
|---|---|---|
| 首页皮肤 | 原生 `HomeActivity` + LibrarySkin | `V15SkinHomeActivity` 挂同一套皮肤 |
| 授权数显示 | `appsModel.grantedCount` 实传 | **已接** `AppsViewModel.grantedCount`（oem5） |
| 设置体量 | `ShizukuSettings.java` ~4KB | ~49KB（大量 Plus 开关） |
| 模块目录 | 精简（home/adb/starter/watchdog…） | 另有 automation/plugin/ota/security/di/health… |

---

## 4. 冷启 / 自启路径

| 组件 | V15 | DropIn-Local | 说明 |
|---|---|---|---|
| `BootCompleteReceiver` Direct Boot 分流 | ✅ | ✅（oem1） | 锁定阶段只武装解锁重试 |
| `UserPresentRestartReceiver` 0/5/15s | ✅ | ✅（oem1） | 解锁后强制重试 |
| `WifiReadyMonitor` | ✅ | ✅（oem1） | Wi‑Fi 就绪再拉起 |
| root `su -c` 兜底 | ✅ | ✅（oem1） | libsu 失败再试原生 su |
| `SelfStarterService` 热路径 FGS | ✅ | ✅ oem6 已迁 | ADB 路径优先直拉 FGS |
| `WirelessBootStartWorker` | ✅ | ✅ oem6 已迁 | 软备份 + TLS 端口发现 |
| `AdbStartWorker` | ❌ | ✅（Plus 回落） | SelfStarter 失败时仍可用 |
| `WatchdogService` | ✅ | ✅ | 两边都有；DropIn 默认开 |

**含义**：冷启「保险绳」（解锁重试 / Wi‑Fi 再拉 / su 兜底）两边对齐；**无线热路径实现仍不同**——小米线若冷启不稳，优先补 SelfStarter，而不是再堆 UI。

---

## 5. 功能矩阵（相对 IMS / 小米线）

### 5.1 两边都有 / 对齐

- 官方包名 Drop-In 兼容（`moe.shizuku.privileged.api`）
- V15 LibrarySkin 首页（结构、主色 `#0B57D0` / Hero `#A9C7FF`、图标）
- Watchdog、开机自启相关设置
- Direct Boot + UserPresent + WifiReady + su -c

### 5.2 仅 DropIn-Local（Plus 底座，白名单默认开）

| 能力 | 默认（oem4） | 备注 |
|---|---|---|
| Custom API 总开关 | ON | |
| Force Start WADB | ON | |
| SU Bridge | ON | |
| Local ADB Proxy | ON | |
| Shell Interceptor | ON | |
| Root Compatibility Hub | ON | `experimental_root_compat` |
| Samsung System UID 1000 | ON | |
| AICore+ | ON | Master/Experimental/NPU 仍 OFF |
| Device Spoofing | ON | |
| Window Manager Plus | ON | |
| Overlay Manager Plus | ON | |
| Network/DNS Governor | ON | |
| Continuity Bridge | ON | |
| Service Doctor | 有入口 | V15 无对应 Activity |
| QS Tile | 有 | V15 无 TileService |
| 应用内更新 / 批量授权等 | 有能力面 | V15 更薄 |

### 5.3 仅 DropIn 存在但白名单默认关（代码在、默认不打扰）

Dhizuku、AVF、Storage Proxy、Activity Manager Plus、NPU/实验 AI、Binder Firewall/Shadow、各类 root ghosting / bootloader 实验项等。

### 5.4 仅 V15 更完整 / DropIn 缺口

| 项 | 状态 |
|---|---|
| `SelfStarterService` 直拉 | DropIn 未迁 |
| `WirelessBootStartWorker` | DropIn 未迁 |
| 首页授权数量 | DropIn `grantedCount=null` |
| 体量与攻击面 | V15 更小、更可控 |

### 5.5 首页卡默认（DropIn 设置）

| 卡 | 默认 |
|---|---|
| 终端 | 隐藏 |
| 了解更多 | 隐藏 |
| 活动日志 | 隐藏 |
| 无线 ADB 启动卡 | 显示 |
| 自动化入口卡 | **显示**（`SHOW_AUTOMATION_HOME` 默认 true；与早期 oem2「隐藏自动化」文案不完全一致，以代码为准） |

---

## 6. 风险与共存

1. **同包名互斥**：装 DropIn dropin 前须卸同包名 V15/官方 Shizuku。  
2. **源码包名不同、运行时包名可相同**：调试日志里是 `af.shizuku.*`，用户侧仍是 `moe.shizuku.privileged.api`。  
3. **未点名能力仍在 APK**：只是默认关；若要「物理删除入口」需另做 UI/清单裁剪。  
4. **全量 `assembleDropinRelease` / 小米真机**：本轮对比未编包装机（历史 Gradle 锁/网络问题）→ 运行时行为标 NOT RUN。  
5. **Pixel 线**：继续 V15，勿用 DropIn 覆盖现网。

---

## 7. 建议使用矩阵

| 机型 | 用哪条 | 原因 |
|---|---|---|
| Pixel（现网稳） | V15 clean | 冷启热路径完整、面更小 |
| 小米 / 重 OEM | DropIn-Local +oem4 | Drop-In 兼容 + Plus 兼容层 + V15 皮 |
| 需要「纯净官方体验」 | 官方 / V15 | 不要上 Plus 面 |

---

## 8. 本轮验证（含 oem7）

| 检查 | 结果 |
|---|---|
| 两边 `versionName` / `applicationId` / flavor | PASS（gradle 检索） |
| 入口继承链 / `grantedCount` | PASS（源码） |
| 冷启组件有无矩阵 | PASS（文件存在性） |
| oem4 默认布尔 | PASS（`ShizukuSettings.java` getBoolean 默认值） |
| `assembleDropinRelease` | **PASS**（2026-07-30，`BUILD SUCCESSFUL`，APK `manager-dropin-release.apk`） |
| 真机安装（`22061218C` / `c0b76e3b`） | **PASS**（卸载签名冲突旧包后安装；`versionName=V15.1.0` / `versionCode=151000`） |
| 冷启广播送达 | **FAIL / 被 OEM 拦截**：`BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED` → `BootCompleteReceiver` 被 MIUI `Security_WakePath Restrict` **SKIPPED**；需在「安全中心 → 自启动」放行后再测 |
| 冷启服务真正拉起 | **NOT RUN**（依赖自启动放行 + 用户开「开机自启」后二次复测） |

---

## 9. 相关文档

- `docs/changes/2026-07-30-shizuku-local-vs-shizukuplus-r2185.md`（改前 Plus vs V15）
- `docs/changes/2026-07-30-shizuku-dropin-local-repo.md`（新仓建立）
- `ShizukuDropIn-Local/README.LOCAL.md`（oem1～oem4 分层）
