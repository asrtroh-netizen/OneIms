# 2026-07-15 · Phase4：通道内嵌 + 通知栏六位码（2.0.19）

## 产品决策
- 成功标准：**有 Wi‑Fi**（不管出门单机从零）
- UX：像 Shizuku——下拉状态栏通知填无线调试六位码
- 不再要求安装独立 `com.oneims.bridge` APK
- 「去 ADB」语义 = 用户面弱化；后台仍用 libadb 做无线调试配对

## 实现
1. `:bridge` 改为 `com.android.library`，由 `:app` 依赖打进主包
2. `bridgeBootShellCommand` 默认 `pm path com.oneims.app` + `BridgeService`
3. `isInstalled` 恒 true；`prepare()` 只开无线调试，不再装包/下载
4. `WirelessPairingNotifier` + `WirelessPairingCodeReceiver`（RemoteInput）
5. 版本 `2.0.19` / code `28`

## 验证
- 单元测试：`OneKukuCoreComponentTest` 宿主包命令
- 真机：连 Wi‑Fi → 启动通道 → 下拉通知填码 → binder 就绪（待装包后跑）
