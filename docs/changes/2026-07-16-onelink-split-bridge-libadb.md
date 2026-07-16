# 2026-07-16 · 第二刀：bridge / libadb 仅 OneKuku

## 动机

OneLink（Shizuku）不应携带 OneBridge library 与内嵌 ADB 依赖。

## 改动

1. Gradle：`onekukuImplementation` → `:bridge`、libadb-android、conscrypt、sun-security  
2. 实现迁至 `app/src/onekuku/java/...`：EmbeddedAdb / MiniAdb / CoreComponent / ResidentService / PairingReceiver / OneBridge*  
3. `app/src/onelink/java/...` 同 FQCN 轻量桩，保证 shared 代码可编译  
4. onelink Manifest 继续 remove Provider/Resident/Pairing，并 remove `com.oneims.bridge` queries  

## 验证

- `compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin` PASS  
- runtimeClasspath：onelink 仅 shizuku；onekuku 含 bridge+libadb  
- `testOnekukuDebugUnitTest --tests OneKukuCoreComponentTest` PASS  
- 双包体积对比 NOT RUN（禁打包）
