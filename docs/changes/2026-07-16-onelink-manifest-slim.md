# 2026-07-16 · OneLink Manifest 精简第一刀



## 动机



Shizuku 线不应再导出 OneBridge Provider、拉起常驻保活、接收无线调试六位码。



## 改动



`app/src/onelink/AndroidManifest.xml` 对以下组件 `tools:node="remove"`：



- `.core.privilege.BridgeBinderProvider`

- `.core.OneKukuResidentService`

- `.core.WirelessPairingCodeReceiver`



仍保留官方 `ShizukuProvider` 与 package queries。



## 验证



- `:app:compileOnelinkDebugKotlin`

- `:app:processOnelinkDebugMainManifest`（随 compile 间接）

- 打包对比 APK 体积：NOT RUN（禁打包）

