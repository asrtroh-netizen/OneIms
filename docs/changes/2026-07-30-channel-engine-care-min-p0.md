> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · ChannelEngine P0：为内置 Shizuku MINI 替换旧桥开开关

## 动机

用户确认：把已裁好的 mini `com.onekuku.care` **能力融合进 OneIMS 内循环**，替换旧特权桥（称新特权桥 / 内置 Shizuku MINI / OneKuku 增强均可）。  
非外置第二 App。

## 本拍交付（第一刀）

- `ChannelEngine`（onekuku）：`ONEBRIDGE` / `CARE_MIN`；进程名常量 `onebridge_server` / `onekuku_server`
- `BuildConfig.CHANNEL_ENGINE` 默认 `"ONEBRIDGE"`（运行时行为不变）
- `ChannelBridgeBootstrap` 按引擎分支（CARE_MIN 暂回落 OneBridge，待 P3b）
- 架构文档升格 B′ + 迁入白名单
- 纠正 Manifest / CoreComponent「Care 融合优先」误导注释

## 验证

```bat
./gradlew :app:testOnekukuDebugUnitTest --tests ChannelEngineTest --tests OneKukuCoreComponentTest
./gradlew :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin
```

## 不做（本拍）

迁 server 源码、改 starter、加 `*.shizuku` Provider、删 `:bridge`。

