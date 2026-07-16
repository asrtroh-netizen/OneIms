# OneLink：新 UI + 2.0.9 激活精华（去糟粕）

日期：2026-07-16

## 定调

| 保留（精华） | 去掉（糟粕） |
|---|---|
| 现首页 StatusHero / 快捷栏新 UI | OneLink 因 stub `hasPairedOnce=false` 误弹内嵌 ADB 三步 `WirelessGuide` |
| 2.0.9「点激活 → 开官方 Shizuku」 | OneLink 钉 CONNECTING / 等六位码 / 走 OneBridge |

## 改动

1. `HomeScreen.kt`：`ChannelLine.usesShizuku` 时主按钮直调 `onActivateOneKuku()`，不弹无线调试说明。
2. `MainActivity.kt`：
   - `prepareOneLinkShizukuChannel()`：2.0.9 风格（IDLE + openShizuku / request / wake）
   - `prepareOneKukuCore()`：Shizuku 线分流到上述函数
   - `beginWirelessPairGuide()`：Shizuku 线直接走 `prepareOneLinkShizukuChannel`（本地函数上移，避免前向引用）
   - `startCoreFromPrepCard()`：仍走 `prepareOneKukuCore`；OneLink 不再写 `NEEDS_ACTIVATION` 开机提示

## 验证

```text
.\gradlew.bat :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin
```

结果：通过（2026-07-16）

真机双包行为：NOT RUN（会话禁打包）
