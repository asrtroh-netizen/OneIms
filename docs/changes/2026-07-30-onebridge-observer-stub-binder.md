# OneBridge Process/UidObserver 空 Binder 导致无法重投

日期：2026-07-30

## 现象

- App 划掉 / `force-stop` / 闪退后，`onebridge_server` 仍存活，但再进 App 等 15s binder 仍不到 → 只能走 ADB 回退，看起来像「未激活」。
- server 进程 log 刷 `IPCThreadState: oneway ... UNKNOWN_TRANSACTION`（code 2/3/4）。

## 根因

`ClientBinderSender` 用 Java `Proxy` 实现 `IProcessObserver` / `IUidObserver`，但 `asBinder()` 返回**空** `Binder()`。

系统侧通过 AIDL `transact` 回调 observer；空 Binder 不会派发到 Proxy 方法 → 回调全部失败 → **永不重投 binder**。

对比 V15 `BinderSender`：使用真实 `ProcessObserverAdapter` / `UidObserverAdapter`（Stub 子类）。

另：投递失败时未清 PID/UID 缓存（缺 V15 #319），失败一次会永久挡重试。

## 修复

1. 反射读取 `$Stub.DESCRIPTOR` / `TRANSACTION_*`，用真实 `Binder.onTransact` 承接回调；Proxy 的 `asBinder()` 指到该 Binder。
2. `onClientReady` 改为返回 `Boolean`；失败则移出 PID/UID 列表允许重试。
3. `OneKukuUserPresentRestartReceiver`：延后 enqueue 使用 `goAsync()`，避免 `Broadcast already finished`。

## 验证（真机 22061218C）

```
# 安装后激活：server 注册 stub
I OneBridge: registerProcessObserver ok (pid-tracked, stub-binder)
I OneBridge: OneIMS pid=… starts; send binder

# force-stop → 再开：server PID 不变，binder 秒级重投
server before/after = 同一 PID
I OneBridge: pid … died
I OneBridge: OneIMS pid=… starts; send binder
I OneBridgeClient: OneBridge binder received
# 无 UNKNOWN_TRANSACTION；冷启可无 ADB fallback 直接 READY
```
