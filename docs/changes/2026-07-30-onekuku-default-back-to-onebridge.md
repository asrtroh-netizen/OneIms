# 2026-07-30 · onekuku 默认通道回 OneBridge

## 决策

用户放弃默认 CARE_MIN（`onekuku_server` / 宿主 MINI）：冷窗、tcpip 踢 adb、hostStable 门禁把体感做退。  
**保留当时功能树**，默认引擎改回 **OneBridge**（`onebridge_server`）。  
（产品号：本地 `3.0.9` 已废止，身份回底包 `3.0.4`。）

## 改动

| 项 | 内容 |
|---|---|
| `app/build.gradle.kts` | `CHANNEL_ENGINE`：`CARE_MIN` → `ONEBRIDGE` |
| `ChannelEngine.kt` 注释 | 默认锚点改回 OneBridge |
| 单测 | `ChannelEngineTest` / `OneKukuCoreComponentTest` 期望对齐 OneBridge |

CARE_MIN 代码路径保留（`HostServerBootstrap` 在非 CARE_MIN 直接 no-op；`ChannelBridgeBootstrap` 仍可按 BuildConfig 切回）。

## 验证

见交付总结命令输出。
