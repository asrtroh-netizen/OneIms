# 2026-07-16 · OneLink BrokerInstrumentation 相对名拒启

## 根因

OneLink `applicationId=com.oneims.onelink`，`namespace=com.oneims.app`。

AGP 会把 `<application>` 内相对组件名展开为 `com.oneims.app.*`，但 **`<instrumentation>` 在 application 外**，合并后仍保留 `.core.BrokerInstrumentation`。

系统按 manifest `package` 解析 → 登记成不存在的 `com.oneims.onelink.core.BrokerInstrumentation`。

`SystemApiBroker` 用 `ComponentName(context, BrokerInstrumentation::class.java)` 启动的是真实类 `com.oneims.app.core.BrokerInstrumentation` → AMS 找不到 instrumentation info → **返回 false**。

2.0.9 单包 `applicationId==namespace==com.oneims.app`，相对名碰巧正确，所以「好好的」。

## 修复

Manifest 改为全限定名：

`android:name="com.oneims.app.core.BrokerInstrumentation"`
