# 划掉后台需再激活：预期 vs Bug 裁决

**日期**：2026-07-15  
**规模**：Bug（现象研判）+ S（总控卡 loading 文案）  
**结论**：**PARTIAL（半预期）**

## 用户现象

划掉 OneIMS 后台再打开，总控卡回到「正在激活 OneKuku」，需再次走激活；截图副文案出现「请保持无线调试配对页面打开…六位码」，主按钮显示「正在恢复…」。

## 裁决

| 现象 | 判定 | 理由 |
|---|---|---|
| 划掉后台后通道「未就绪」 | **预期内** | OneBridge binder 存在于 OneIMS 进程内存（`BridgeBinderHolder`）；进程被杀后 binder 必丢，`isReady()` 为 false。文档已写明 force-stop 后需再 start。 |
| 已配对设备重开后自动无码直连 | **产品意图** | `hasPairedOnce` 时首页 `LaunchedEffect` 自动 `prepareOneKukuCore()`，优先 `wake()` / 直连，不预先挂六位码。 |
| 已配对仍落到「等六位码」 | **环境/失败路径，非「设计要每次配对」** | 无线调试被系统关掉、Wi‑Fi 未连、ADB 端口不可达时 `NeedPairingCode`；或从未写过 `has_paired_once`。 |
| 激活中主按钮显示「正在恢复…」 | **Bug（文案）** | `StatusHero` 在 `loading=true` 时硬编码 `onekuku_action_running`，覆盖激活中文案。 |

## 对外答复口径（可直接转用户）

> 划掉后台等于结束 App 进程，特权通道 binder 会断，这是 Android 侧正常现象，不是「配置丢了」。  
> 若你以前配对成功过：重新打开后一般会自动静默重连，不必再填六位码；若系统关掉了「无线调试」或没连 Wi‑Fi，才可能再要一次码。  
> 通话配置快照仍保存在本机，通道就绪后可一键恢复。

## 代码锚点

- Binder 进程内：`OneBridgePrivilegeBridge` / `BridgeBinderHolder`
- 杀进程需再起：`docs/changes/2026-07-15-onebridge-binder-delivery-fix.md`
- 已配对自动连：`MainActivity.kt` `LaunchedEffect(Unit)` → `prepareOneKukuCore()`
- 双路径：`docs/changes/2026-07-15-dual-path-paired-reconnect.md`
- 文案修复：`OneImsComponents.kt` `loadingText = actionLabel`

## 本轮改动

- 修复激活中 loading 误用「正在恢复…」
