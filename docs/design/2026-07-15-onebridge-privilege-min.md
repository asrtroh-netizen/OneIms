# 立项：OneIMS 自研微型特权桥（OneBridge）

**日期**：2026-07-15  
**状态**：已立项 · Phase 0（契约与边界）  
**代号**：OneBridge（对内；产品面可继续叫 OneKuku 核心能力，不再伪装成「另一个商店 App」）  
**规模**：L  
**决策**：不做换皮 Shizuku；不做完整 Shizuku 克隆；做**最小 shell binder 桥**，只服务 OneIMS。

---

## 0. OneKuku 产品意义（硬约束）

> **OneKuku 的意义 = 只要 OneIMS 自己需要的东西，不要 Shizuku 的完整功能。**

这不是口号，是裁剪闸门：

| 要 | 不要 |
|---|---|
| 无线调试自拉起 + 自家 shell 桥 | 完整 Shizuku Manager / 通用授权商店 |
| `ping` / 授权 / binder 上下线 | `newProcess`、用户服务、多 App 生态协议 |
| 仅包装 `activity` · `carrier_config` · `isub` · `phone` | 任意 system service 透传、rish、完整 API 面 |
| 签名白名单只放行 OneIMS | 第三方 App 接入、换皮品牌第二商店 |
| 产品话术「启动通道」 | 「再装一套 Shizuku / Core」叙事 |

**膨胀禁令**：任何「以后可能有用」的 Shizuku 能力，默认 **OUT**；只有 `SystemApiBroker` / 守护 / 磁贴实测缺口才能进 MVP。换皮 Core 已证伪：皮换了，功能全集还在，对 OneIMS 零增益。

产品名可继续叫 OneKuku；工程实现叫 **OneBridge**——名归产品，体归最小集。

### 0.1 成功标准：户外急救（不是「零调试」）

用户已接受：**完全不开无线调试不行**。真正要的是：

> **没电脑、人在户外时，仍能靠 OneIMS 内嵌 ADB + 无线调试，把特权通道拉起来做急救恢复（通话/IMS 等）。**

| 场景 | 目标体验 |
|---|---|
| 家里首次 | 开开发者选项 → 开无线调试 → App 弹配对码 → 内嵌 ADB 配对并 `start` |
| 出门急救 | **不再找电脑**；开发者选项/无线调试仍可能要开；点「启动通道」自拉起 |
| 已 tcpip 保活 | 关公网 Wi‑Fi 后仍尽量用 `127.0.0.1:5555` 自连（已有 persist 路径） |
| 重启后 | 通常需再点一次拉起（非 Root 无法永久守护） |

**现有底座**：`OneKukuEmbeddedAdbActivator`（pair → connect → `tcpip:5555` → `start.sh`）已对准户外急救；缺口是 `start.sh` 仍指向外置 Core/Shizuku，而不是自家 OneBridge。

---

## 1. 为什么立项

当前路径（换皮 Core / 诱导装第二套 Shizuku 皮）对用户是多此一举：

- 能力仍依赖 `rikka.shizuku` 客户端 + shell 级服务端
- 无线调试配对、授权弹窗、第二 APK 一个不少
- 品牌收益低、维护与困惑成本高
- **违背 OneKuku 本意**：要的是自家刚需，不是再搬运一套完整提权框架
- **户外急救被第二 APK 拖累**：出门急救还要保证「皮」已装、能起，心智成本过高

目标：用**同等原理**（ADB 拉起 shell 进程 → 借 binder 写系统 API）做**更小、只服务 OneIMS** 的桥，最终去掉对外部 Shizuku 应用的产品依赖；**内嵌 ADB 负责户外无电脑拉起**。

---

## 2. OneIMS 真实依赖面（刚需）

### 2.1 生命周期 / 授权（客户端）

| 能力 | 现用 API | MVP 是否必须 |
|---|---|---|
| 服务是否在跑 | `Shizuku.pingBinder()` | 必须 |
| 是否已授权 | `Shizuku.checkSelfPermission()` | 必须 |
| 请求授权 | `Shizuku.requestPermission()` | 必须 |
| binder 上下线 | `OnBinderReceived/DeadListener` | 必须（守护/磁贴） |
| 是否 root 通道 | `Shizuku.getUid() == 0` | 可延后（有则加速） |

### 2.2 系统服务包装（写入刚需）

`SystemApiBroker` 经 `ShizukuBinderWrapper(SystemServiceHelper.getSystemService(...))` 使用：

| 服务名 | 用途 | MVP |
|---|---|---|
| `activity` | AMS / Instrumentation 委托写 | 必须 |
| `carrier_config` | CarrierConfig 覆盖 | 必须 |
| `isub` | Subscription 相关 | 必须 |
| `phone` | Telephony 相关 | 必须 |

结论：MVP 不是「通用提权框架」，而是：

> **稳定提供「shell 身份的 IBinder → 指定 system service」+ 对 `com.oneims.app` 的授权门禁。**

---

## 3. 方案矩阵（已选）

| 方案 | 结论 | 原因 |
|---|---|---|
| 继续换皮 Core | ❌ 不选 | 无功能增益，产品叙事自欺 |
| 诚实用官方 Shizuku | 过渡可保留 | 最小风险回落；立项期间双轨 |
| **自研 OneBridge** | ✅ 主路径 | 满足「不要绕回 Shizuku 皮」；范围可控 |
| 纯 ADB 短生命周期 | 辅助 | 可做冷启动写入；不覆盖磁贴/守护常驻 |
| Root/Magisk | 非默认 | 与免 root 用户群冲突 |

**选定**：OneBridge 主路径；官方 Shizuku 仅作 Phase 1–2 回落，直到桥验收通过。

---

## 4. 架构不变量

1. **启动**：仍需用户侧无线调试/ADB（或 root）拉起 bridge 进程——系统红线不消失。  
2. **信任边界**：bridge 只向签名匹配的 OneIMS（及明确白名单）授权。  
3. **不做**：通用 App 商店式权限管理、多用户完整复刻、与上游协议兼容伪装。  
4. **单一真源**：OneIMS 只面对 `PrivilegeBridge` 接口；底层 Shizuku / OneBridge 可切换。  
5. **可回滚**：开关/编译期 flavor 可退回 Shizuku，直到桥稳定。

---

## 5. 契约草案（Phase 0）

### 5.1 进程与包

| 项 | 约定 |
|---|---|
| 服务端包名 | `com.oneims.bridge`（暂定） |
| 显示名 | OneIMS Bridge / OneKuku 通道（产品拍板） |
| 启动脚本 | `/Android/data/com.oneims.bridge/start.sh`（或等价） |
| 客户端 | 仅 `com.oneims.app`（签名校验） |

### 5.2 客户端接口（替换 `OneKukuManager`/`ShizukuManager`）

```text
interface PrivilegeBridge {
  fun isRunning(): Boolean
  fun isGranted(): Boolean
  fun isReady(): Boolean = isRunning() && isGranted()
  fun requestPermission(requestCode: Int)
  fun addBinderListeners(...)
  fun wrapSystemService(name: String): IBinder  // 替代 ShizukuBinderWrapper+getSystemService
  fun getUid(): Int
}
```

### 5.3 服务端最小职责

1. ADB/`app_process` 拉起后持有 shell 身份  
2. 向客户端提供 system service binder 包装  
3. 授权 UI：允许/拒绝 OneIMS  
4. 死亡通知 / ping  

---

## 6. 分期计划（文件级）

### Phase 0 · 契约与门面（本立项交付）✅

| 文件/产物 | 动作 |
|---|---|
| `docs/design/2026-07-15-onebridge-privilege-min.md` | 本文 |
| `core/privilege/PrivilegeBridge` + `ShizukuPrivilegeBridge` + `PrivilegeBridges` | ✅ 已落地；`OneKukuManager` / `SystemApiBroker` / `ShizukuManager` 已改走门面 |

### Phase 1 · MVP 服务端（`:bridge` 模块）

| 项 | 内容 |
|---|---|
| 模块 | OneIMS 仓内 `:bridge`（`applicationId=com.oneims.bridge`） |
| 产物 | `bridge-debug.apk` + 运行时写出的 `start.sh` |
| 状态 | **脚手架 + assets 内置已落地**（2026-07-15）；真机 binder 联调待验 |
| 客户端 | `PrivilegeBridges` 优先 OneBridge，回落 Shizuku |
| start 指向 | `CANDIDATE_PACKAGES` 首项已是 `com.oneims.bridge` |
| 内置 APK | `app/src/main/assets/oneims-bridge.apk`（安装优先；`onekuku-core.apk` 过渡回落） |

### Phase 2 · OneIMS 切换

| 文件 | 动作 | 状态 |
|---|---|---|
| `OneKukuManager` / `ShizukuManager` | 改为委托 `PrivilegeBridge` | ✅ Phase0 |
| `SystemApiBroker` | `wrapSystemService` 经门面 | ✅ Phase0 |
| `GuardService` / `MainActivity` | 改听 `PrivilegeBridges` 生命周期（Tile/Diagnostics 本就走 Manager 轮询） | ✅ 2026-07-15 |
| 首页激活卡 | 文案「启动通道」；去掉用户可见「换皮 Core」叙事 | ✅ 2026-07-15 |
| 回落 | 运行时 `FallbackPrivilegeBridge`（本轮不做 BuildConfig 开关） | ✅ |

### Phase 3 · 卸载上游依赖

| 项 | 内容 | 状态 |
|---|---|---|
| 移除 | 对外部 Shizuku App / 换皮 Core 的安装引导与探测 | ✅ 2026-07-15 |
| 移除 | `rikka.shizuku` Maven + `ShizukuProvider` + `ShizukuPrivilegeBridge` | ✅ 2026-07-15 |
| 清理 | `assets/onekuku-core.apk`；`CANDIDATE_PACKAGES` 仅 `com.oneims.bridge` | ✅ 2026-07-15 |
| 可选 | 邻仓 `OneKukuCore` fork 归档 | 文档建议；本仓不强制 |

**生产路径**：`PrivilegeBridges.current = OneBridgePrivilegeBridge()`。无线调试红线仍在。

---

## 7. 诚实边界（必须对用户说清）

- **不能取消**无线调试/配对（除非 root）。微型桥不改变系统安全模型。  
- **不能**「一个 APK 内普通权限写死 CarrierConfig」。  
- 自研桥的工作量在 **服务端正确性与 OEM 差异**，不在改包名。  
- Phase 1 完成前，生产仍可用官方 Shizuku 回落，避免空窗。

---

## 8. 验证矩阵（立项验收）

| 阶段 | 命令/场景 | 通过标准 |
|---|---|---|
| Phase 0 | 文档评审 | 边界/不变量无歧义 |
| Phase 1 | 真机 adb start → ping → wrap carrier_config | 非 OneIMS 包拒绝；OneIMS 可读/写探测 |
| Phase 2 | 一键恢复通话 / 磁贴切卡 | 与 Shizuku 路径行为一致 |
| 回归 | 关 bridge 回落 Shizuku | 开关可逆 |

---

## 9. 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| SELinux / OEM 杀进程 | 高 | 保活策略保守；失败可观测 |
| 重造安全漏洞 | 高 | 签名白名单；最小 API；审计 |
| 工期膨胀成「第二个 Shizuku」 | 高 | 冻结范围：仅 4 个 system service + 授权 |
| 许可证 | 中 | 不复制上游源码；独立实现；文档注明 |

---

## 10. 下一步（待你点选）

1. **Phase 1 开干**：邻仓/模块脚手架 + `start.sh` + ping  
2. **先做 PrivilegeBridge 门面**：OneIMS 内先抽象接口，底层仍走 Shizuku（为切换铺路）  
3. **并行保留官方 Shizuku 引导**：产品诚实化，去掉换皮 Core 强制叙事  

**建议顺序**：2 → 1 → 3（先解耦客户端，再造服务端，再改产品话术）。
