> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · CARE_MIN：邻仓 server 最小面 vendor 进 `:care-min`

**状态**：`:care-min:compileDebugKotlin` BUILD SUCCESSFUL  
**默认引擎**：`BuildConfig.CHANNEL_ENGINE` 仍为 `ONEBRIDGE`（未改）

## 做了什么

1. 从 `E:\GQ\One\_forks\ShizukuDropIn-Local` vendor 闭包进 `care-min/vendor/`：
   - server 白名单类（无邻仓 *PlusImpl 原文件）
   - common / starter / shared / server-shared / aidl / rish(+cpp)
   - BinderContainer（provider 最小面）
2. 宿主化：
   - `ServerConstants.MANAGER_APPLICATION_ID` / `HOST_APPLICATION_ID` → `com.oneims.app`
   - `ShizukuService.main` Ddm → `onekuku_server`
   - `getManagerApplicationInfo` 优先宿主
3. `CareMinPlusStubs.kt`：编译桩满足 Plus AIDL 符号（不复制邻仓 PlusImpl 实现）
4. `care-min/build.gradle.kts`：sourceSets + aidl + hidden-compat/refine/gson/timber/coroutines

## 验证

```bat
./gradlew :care-min:compileDebugKotlin
```

→ `BUILD SUCCESSFUL`

## 后续

- 挂 app onekuku `implementation(project(":care-min"))` 使类进 APK（P3a 续 / P3b）
- rish NDK/`libcxx` prefab 真机链路；默认切 `CARE_MIN` 前需真机写配置 PASS

