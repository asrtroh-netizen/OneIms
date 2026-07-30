# 2026-07-30 · CARE_MIN：transactRemote code=1 未处理导致 AM reject

## 现象（Pixel 9 Pro Fold / Android 17 / 3.0.9-onekuku）

- `ActivityManager rejected BrokerInstrumentation (channel=onekuku, bridgeUid=2000, appUid=…)`
- `binder dead` 重连风暴；外置 V15 安装时易出现「ACTIVE 但无 onekuku_server」
- 同机 `am instrument --no-restart … BrokerInstrumentation`（shell）可成功 onCreate

## 根因

1. 客户端 `ShizukuBinderWrapper` 用 **binder transaction code 1** 调 `transactRemote`；`IShizukuService.aidl` 编号方法从 2 起，**没有** code 1。
2. Server 依赖 hidden `Parcel.readInterfaceToken` 解析 descriptor 后再分发 code 1；解析失败时 descriptor 为空，code 1 落入未处理，客户端读到空 reply → `startInstrumentation` 被当成 `false` → 文案「AM rejected」。
3. 产品目标是内嵌完整 Shizuku（`onekuku_server`）；激活路径若只认外置 binder，会误判 READY。

## 修复

- `Service.onTransact`：对 code 1 **优先**用公开 API `enforceInterface(BINDER_DESCRIPTOR)` 后调用 `transactRemote`。
- `transactRemote` appendFrom 失败时 `writeException`，禁止空 reply。
- `CareMinBootShell`：优先扫描安装态 `lib/arm64`（再 ABI 名）。
- 激活 / 开机：CARE_MIN 必须 `OneKukuHostServerBootstrap.ensureRunning`，不能仅靠外置 V15 的 isReady。

## 验证

- 重装 onekuku debug → 杀旧 `onekuku_server` → 激活 → `pidof onekuku_server` 有值
- 能力页「应用核心能力」：不再出现 AM rejected；CarrierConfig 写入成功或给出真实 Broker 错误
