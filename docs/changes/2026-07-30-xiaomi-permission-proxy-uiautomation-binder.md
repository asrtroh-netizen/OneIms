# 2026-07-30 · 小米 HyperOS：权限代理（Instrumentation）失败诊断

**机型**：`22061218C`（zizhan）· HyperOS `V816` · Android 15（SDK 35）  
**制品**：OneIms Lite `3.1.0-onelink`（vc80）+ Shizuku Plus `V15.1.0` persist-tcp（vc151000）  
**本轮**：诊断-only（未改业务代码）

## 现象

IMS 能力页点「推荐一键开启」后，权限代理路径起不来，CarrierConfig 写入中断。

## 证据（logcat · pid `com.oneims.onelink`）

```
I OneIMS-OneKuku: write target=… reason=applyAll …
D UiAutomationConnection: Created on user UserHandle{0}
W JavaBinder: ibinderForJavaObject: … is not a Binder object
E Parcel  : Native binder in markForBinder is null for non-null jobject
W OneIMS-SandboxPersist: startInstrumentation(sandbox) returned false
D UiAutomationConnection: Created on user UserHandle{0}
W JavaBinder: ibinderForJavaObject: … is not a Binder object
E Parcel  : Native binder in markForBinder is null for non-null jobject
D ActivityThread: Too many transaction errors, throttling freezer binder callback.
```

复现命令（设备在线时）：

```text
adb logcat -c
# 在 App 点「推荐一键开启」或 adb shell input tap <中心坐标>
adb logcat --pid=$(adb shell pidof com.oneims.onelink) | findstr /i "Sandbox Persist UiAutomation JavaBinder Broker OneIMS"
```

## 根因（5-Why）

1. **表象**：权限代理失败 / 配置未写入。  
2. **直接机制**：`SandboxPersistSupport` 与 `SystemApiBroker.startBrokerInstrumentation` 都经 `PrivilegeBridges.wrapSystemService("activity")`（ShizukuBinderWrapper）调用 `IActivityManager.startInstrumentation`，并把本地 `new UiAutomationConnection()` 作为 `IUiAutomationConnection` 参数传入。  
3. **封送失败**：在 App 进程写 Parcel 时，该 jobject **没有原生 Binder peer**（`ibinderForJavaObject is not a Binder object` / `markForBinder is null`），事务损坏 → `startInstrumentation` 返回 `false`。  
4. **双路径同死**：沙盒持久旁路失败后回落 Broker 主路径，第二次创建 `UiAutomationConnection` 再次踩同一坑。  
5. **环境叠加**：刚重装后 `WRITE_SECURE_SETTINGS` 对 `com.oneims.onelink` 初始未 grant（本轮诊断中已用 `pm grant` 补上）；但这只是次要条件——**即便已 grant，Instrumentation 权限委托仍过不了 binder 封送**。

相关源码：

- `app/src/main/java/com/oneims/app/core/SandboxPersistSupport.kt`（`startSandboxInstrumentation`）
- `app/src/main/java/com/oneims/app/core/SystemApiBroker.kt`（`startBrokerInstrumentation`）
- `app/src/main/java/com/oneims/app/core/privilege/ShizukuPrivilegeBridge.kt`（`ShizukuBinderWrapper`）

历史相近（机制不同、文案相同）：`docs/changes/2026-07-16-broker-am-reject-no-fake-rollback.md`、`docs/changes/2026-07-30-care-min-transactremote-code1-fix.md`（transactRemote code=1 空 reply）。**本机本次日志钉的是 UiAutomationConnection 本地 Binder peer 为空，不是 stopDelegate / code=1 考古案。**

## 已排除

| 假设 | 结果 |
|---|---|
| Shizuku 服务未起 | 排除：`shizuku_plus_server`（shell）+ App 进程在跑；`API_V23` 已 grant |
| 仅沙盒开关问题 | 排除：Broker 主路径同样 binder 失败 |
| 仅缺 WRITE_SECURE_SETTINGS | 部分成立（已 grant）；**不足以单独解释** Instrumentation 起不来 |

## 修复方向（尚未落地）

1. **优先**：在 HyperOS 15 上确认 `UiAutomationConnection` 是否仍为真正的 `Binder` 子类；必要时改为显式 `asBinder()` / 官方推荐构造，或由 **Shizuku server 进程内**发起 `startInstrumentation`（避免 App 侧封送该对象）。  
2. **服务端**：复查 V15.1.0 `transactRemote` 对「调用参数里携带本地 Binder」的拷贝是否完整（与 code=1 空 reply 同类硬化）。  
3. **诊断 UX**：把 `ibinderForJavaObject / markForBinder` 摘要写进排障日志与 Snackbar，避免只显示笼统「权限代理启动失败」。  
4. **验收**：一键写入后 logcat 不再出现上述两条；`SystemApiBroker.lastStrategy` 变为 `instrumentation-shell-delegate` 或合法降级策略；设备详情「权限代理」为支持。

## 本轮副作用

- 已执行：`pm grant com.oneims.onelink android.permission.WRITE_SECURE_SETTINGS`（granted=true）。  
- 未改 APK / 未改仓库业务代码（仅本诊断文档）。
