# 2026-08-05 · Root 开机自启仅永久 Root 显示

## 版本

| 项 | 值 |
|---|---|
| versionName | `3.3.1`（覆盖 3.3.0） |
| versionCode | `101` |
| 双线 | OneKuku + OneLink 同源 |

## 需求

临时 Root 时不要显示「Root 开机自启」；必须持久/永久 Root 才显示。

## 方案

| 显隐 | 条件 |
|---|---|
| ROOT 徽标 | any Root（临时琥珀 / 永久黑金）不变 |
| 运营商写入 / 临时 Root 工具 | any Root（`showRootFeatures`）不变 |
| Root 开机自启（首页 + 实验页） | **仅** `permanent`（`showRootBootStart`） |

单一真源：`RootPresenceProbe.Snapshot.showRootBootStart`。

## 关键落点

- `RootPresenceProbe.kt`
- `HomeScreen.kt`（OneKuku / OneLink 双首页）
- `ExperimentalScreen.kt`
- `UiModels.kt` / `MainActivity.kt`
- `RootPresenceProbeTest.kt`

## 验证

```text
.\gradlew :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin :app:testOnekukuDebugUnitTest --tests com.oneims.app.core.RootPresenceProbeTest
→ BUILD SUCCESSFUL
```
