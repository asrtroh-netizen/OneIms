# 2026-07-25 · OneTools 做减法（轻度小工具）

## 背景

用户明确纠偏：查号不要；Meter 重功能也不再学习/堆叠；OneTools 定位为 **轻度、好用的配套小工具**。

## 决策

| 去掉 | 保留 |
|---|---|
| OneCaller / 查号 / CallScreening / Directory Provider | Shizuku 四态首页壳 |
| Meter 全家桶（悬浮、芯片、Tile、分应用流量、套餐限额） | 电池只读 |
| 应用内 Pixel Telo 对照页 | 通话录音（自研干净室） |
| Room / blocklist / Usage Access / 悬浮窗权限面 | 应用更新中心 |
| | 诊断导出（F4） |

## 落地

- 首页与 `MainActivity` 路由只剩：电池 / 录音 / 更新 / 导出
- Manifest 移除 Meter/Caller 组件与多余权限；去掉 `pixel.telo` queries
- 删除 `meter/`、`caller/` 源码与对应单测；删除 `MeterScreen` / `CallerScreen` / `TeloScreen`
- `build.gradle.kts`：去掉 Room/KSP、`ONE_BLOCKLIST_URL`；`versionName=0.1.0-lite`
- 文案与 `NOTICE`、架构蓝图 Out 墙同步

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
