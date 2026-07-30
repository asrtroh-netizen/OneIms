# 2026-07-30 · Care / Shizuku MINI server 迁入白名单（宿主内嵌）

**状态**：冻结（P3a 执行依据）  
**目标**：把邻仓 `E:\GQ\One\_forks\ShizukuDropIn-Local` 的 **server 最小面** 融进 OneIMS 宿主，替换旧 OneBridge 引擎；**禁止**整仓 Manager UI。

相关：`2026-07-30-onekuku-care-home-fusion.md` · `ChannelEngine` · `2026-07-30-onekuku-mini-from-v1510.md`

## 1. 迁入后契约（宿主）

| 项 | 值 |
|---|---|
| applicationId | `com.oneims.app`（不变） |
| 进程 nice-name | `onekuku_server`（避开邻仓/Plus 的 `shizuku_plus_server`） |
| Provider | `com.oneims.app.shizuku` |
| Manager / 白名单 | 仅宿主 uid / `com.oneims.app` 静默授权 |
| 客户端 | `PrivilegeBridge` ← Shizuku 同构实现 |
| MVP 服务闸门 | 仍限 `activity` / `carrier_config` / `isub` / `phone` / `package` |

## 2. 允许迁入（IN）

相对邻仓路径（按目录；实际落点可为 `:care-min-server` 或 `app/src/onekuku`）：

| 邻仓路径 | 用途 |
|---|---|
| `server/src/main/java/rikka/shizuku/server/ShizukuService.java` | server 入口（需改 MANAGER_APPLICATION_ID→宿主、Ddm 名→`onekuku_server`） |
| `server/src/main/java/rikka/shizuku/server/BinderSender.java` | binder 投递 |
| `server/src/main/java/rikka/shizuku/server/ShizukuClientManager.java` | 客户端管理 |
| `server/src/main/java/rikka/shizuku/server/ShizukuConfigManager.java` | 配置 |
| `server/src/main/java/rikka/shizuku/server/ShizukuUserServiceManager.java` | 若 MVP 不需要 UserService，P3a 可再裁 |
| `server/src/main/java/rikka/shizuku/server/ServerConstants.java` | 常量（改宿主 id） |
| `server` 内 Android 16/17 兼容辅助（`util/*`、`Android17Compat` 等刚需） | 冷启/binder 契约 |
| `starter` 中 `app_process` / `libshizuku` 拉起所需最小 JNI/脚本逻辑 | 等价于现 `bridgeBootShellCommand` |
| `api` / `provider` 客户端最小面（若 onekuku 复用 rikka API） | 与 onelink 对齐契约 |
| V15 冷启精华对照（只读移植逻辑，非整模块）：UserPresent / WifiReady / Watchdog / SelfStarter 思路 | 已部分在 OneIMS；缺口按真机补 |

## 3. 禁止迁入（OUT）

| 项 | 理由 |
|---|---|
| `manager/` 全部 UI（首页/设置/终端/活动日志/了解更多） | 用户路径在 OneIMS 首页 |
| `automation` / AutomationService / AICore+ / Locale 插件 | MINI 已砍；IMS 无关 |
| Service Doctor / Root Compatibility Hub / Samsung UID1000 默认开 | 见 cut-advice |
| Local ADB Proxy(15555) / Shell Interceptor | 与内嵌 ADB 路径重叠/增攻击面 |
| Plus 全家桶 API、批量授权、应用内更新、QS Tile、SU Bridge | privilege-min 膨胀禁令 |
| 把 `applicationId` 改成 `com.onekuku.care` | 破坏单包身份 |
| 强制用户安装外置 `com.onekuku.care` | 违内循环冻结 |

## 4. 迁移顺序闸门

1. **P0（已开）**：`ChannelEngine` 默认 `ONEBRIDGE` + 本文档  
2. **P3a 客户端+模块骨架（已开）**：`:care-min` 常量/boot；onekuku 挂 Shizuku API + Provider；`CARE_MIN`→`ShizukuPrivilegeBridge`；boot 命令按引擎选入口；**server 源码闭包尚未打进 APK**  
3. **P3a 续**：按本白名单把 server 最小面编进 `:care-min` / 宿主；默认仍不启用  
4. **P3b**：`CHANNEL_ENGINE=CARE_MIN`；真机写配置 PASS  
5. **P3c**：退役 `:bridge` / `onebridge_server`

## 5. 验证（P3a 起）

| 检查 | 标准 |
|---|---|
| 编译 | `./gradlew :app:assembleOnekukuDebug` |
| 默认引擎 | 未改 BuildConfig 时仍为 OneBridge 行为 |
| 体积/入口 | APK 无 Manager 主界面 Activity |
| 负向 | 不要求安装 `com.onekuku.care` |
