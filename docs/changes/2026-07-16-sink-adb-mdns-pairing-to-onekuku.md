# 下沉 OneBridge/ADB 死代码到 onekuku（第三刀）

日期：2026-07-16

## 动机

OneLink 是独立 IMS + Shizuku 产品线；main 中残留的 mDNS / 六位码通知 / OneBridge 组件声明仍会进 onelink APK。

## 改动

### 源码

| 从 main 移除 | onekuku 实现 | onelink 桩 |
|---|---|---|
| `OneKukuAdbMdns.kt` | ✅ 完整 | 空实现（Wi‑Fi/mDNS 恒假） |
| `OneKukuPairingNotification.kt` | ✅ 完整 | no-op + 同名常量 |
| `OneKukuAdbActivationBridge.kt` | ✅ 接口 | 无（仅 onekuku 引用） |

（此前已下沉：EmbeddedAdb / MiniAdb / OneBridge* / BridgeBinder* / Resident / PairingReceiver）

### Manifest

- 新增 `app/src/onekuku/AndroidManifest.xml`：`CHANGE_WIFI_MULTICAST_STATE`、`com.oneims.bridge` queries、`BridgeBinderProvider`、`OneKukuResidentService`、`WirelessPairingCodeReceiver`
- `main` 去掉上述 OneKuku 专用项
- `onelink` Manifest 仅保留官方 `ShizukuProvider` + Shizuku package queries（不再需要 `tools:node=remove`）

### Assets

- `ONEKUKU_CORE_README.txt` → `app/src/onekuku/assets/`

## 验证

```text
.\gradlew.bat :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin
.\gradlew.bat :app:processOnekukuDebugMainManifest :app:processOnelinkDebugMainManifest
```

合并 Manifest：onekuku 含 BridgeBinder/Resident/Pairing/Multicast；onelink 仅 ShizukuProvider。
