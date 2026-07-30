> ⚠️ **已废止现行方案（2026-07-30）**：OneKuku 迷你版 / CARE_MIN 已清除。见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · OneIMS 专用迷你 OneKuku：在现有保留清单上再砍什么

**类型**：架构建议（本拍不改业务源码）  
**前提**：用户已圈定的保留面（见下）+ 既有  
`2026-07-30-v1510-strip-candidates.md`、`2026-07-15-onebridge-privilege-min.md`、  
`2026-07-30-onekuku-mini-from-v1510.md`

## 0. 你当前保留面（本轮输入）

| 类 | 保留项 |
|---|---|
| 一类精华 | A16/17 兼容、Watchdog、Service Doctor、Force WADB、Drop-In |
| 兼容层 | Local ADB Proxy(15555)、Shell Interceptor、Root Compatibility Hub、Samsung UID1000 |
| V15 冷启/热路径 | Direct Boot · UserPresent 0/5/15 · WifiReady · su -c · SelfStarter · WirelessBootStartWorker · TCP 优先 · 闪退修复 |
| UI | 无线 ADB / 授权 / 开机自启 / Watchdog / **自动化入口卡**；隐藏终端·了解更多·活动日志 |

## 1. OneIMS 专用成功标准（裁剪闸门）

> 只保证：无线/ADB 拉起 → binder 就绪 → 授权 OneIMS → 能写 `activity` / `carrier_config` / `isub` / `phone` → 冷启/划掉后能秒醒。

**不要**：通用 Plus API 商店、多 App 生态、排障全家桶、OEM 特化全家桶。

## 2. 建议再砍（稳优先 · 高价值减法）

相对你现清单，**建议立刻再砍**（软关 → 再物理删）：

| 优先级 | 项 | 建议 | 理由（稳） |
|---|---|---|---|
| P0 | Shell Interceptor | **砍** | OneIMS 不经这层；多一层 shell 劫持=多攻击面与冷启变量 |
| P0 | Local ADB Proxy (15555) | **砍** | 与 SelfStarter / Force WADB / 官方无线路径功能重叠；户外急救不依赖 15555 代理 |
| P0 | 自动化入口卡 + `automation` 模块 | **砍** | 对 IMS 是噪音；与「启动通道」心智冲突 |
| P1 | Service Doctor | **砍出默认产物**（可留 debug flavor） | 排障工具，不是运行时刚需；体积与入口噪音 |
| P1 | Root Compatibility Hub | **砍** | 非 Pixel/主路径刚需；Root 用户另开 flavor 即可 |
| P1 | Samsung UID1000 | **砍出默认**（三星 OEM flavor 再开） | 非全机型；默认开会拖宽权限面 |
| P2 | 已隐藏的终端 / 活动日志 / 了解更多 | **物理删模块** | 隐藏仍占体积；MINI 不该留尸 |
| P2 | 批量授权 / 应用内更新 / QS Tile / SU Bridge / 全部 Plus API | 你已未列入 → **维持剔除** | 与立项「不要完整 Shizuku」一致 |

## 3. 建议必留（再砍会伤稳）

| 项 | 为何不能砍 |
|---|---|
| A16/17 兼容 | 新机 binder/启动契约 |
| Watchdog | 假死对照刚需 |
| Force WADB | OEM 无线调试难拉时的救命开关（可默认关实现、**勿删代码**） |
| 整包 V15 冷启/热路径 | 今日 OneKuku 假死对照的参照系；MINI 的核心价值 |
| 无线 ADB 卡 + 授权 + 开机自启 | 用户完成「启动→授权」的最小闭环 |
| Server 最小面 + 客户端 API | `ping` / 授权 / binder；`SystemApiBroker` 四服务 |

## 4. 「Drop-In」要单独拍板

两条路互斥叙事，别混：

| 路线 | 包名 | 含义 |
|---|---|---|
| A · 外置瘦 Shizuku | `moe.shizuku.privileged.api` | 真 Drop-In；可替官方；与 stock 互斥 |
| B · OneKuku MINI 独立通道 | `com.oneims.onekuku.core`（已有 flavor 稿） | **不必**强留 Drop-In 官方包名；可与官方并存；OneIMS 显式绑定 |

若目标是「对 OneIMS 专用迷你 OneKuku」→ 推荐 **B**，此时清单里的「Drop-In」应改口为：**兼容 Shizuku 客户端契约（API 面）**，而不是官方包名互斥安装。

## 5. 推荐最终保留面（OneIMS-MINI）

```
必留运行时
├─ A16/17 兼容
├─ Force WADB（实现保留，设置可藏）
├─ Watchdog
├─ V15 冷启/热路径全集（你列的那串）
├─ 无线 ADB + 授权 + 开机自启（UI）
└─ Server/API 最小面（四系统服务写入）

默认砍掉（相对你现清单）
├─ Service Doctor（或仅 debug）
├─ Local ADB Proxy
├─ Shell Interceptor
├─ Root Compatibility Hub
├─ Samsung UID1000（默认）
└─ 自动化入口卡 / automation
```

体量锚点：逼近 **V15.0.0 clean + oem1/oem6 冷启差分**，而不是 oem4 白名单子集。

## 6. 与已有工程的衔接

- `onekukuMini` flavor 已软裁（Plus getter 全关、去 Feature Hub）→ 下一步应对齐本清单做 **物理删 / strip**。  
- 撤内嵌 OneBridge 前：先用 MINI 包跑通 OneIMS 写配置，再拆 `:bridge`。  
- 验证闸门：`assembleOnekukuMiniRelease` + 真机「划掉/冷启/写 carrier_config」三连；未跑标 NOT RUN。

## 7. 本拍验证

| 检查 | 结果 |
|---|---|
| 对照 strip-candidates / privilege-min / mini-from-v1510 | PASS（本轮 Read） |
| 实际再砍代码 / 编包 | **NOT RUN**（本拍仅建议） |

