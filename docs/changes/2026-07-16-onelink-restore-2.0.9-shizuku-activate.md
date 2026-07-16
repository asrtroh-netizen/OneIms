# 2026-07-16 · OneLink 激活对齐 2.0.8/2.0.9

## 锚点

`cf12fb6`（2.0.9）/ `94cd775`（2.0.8）：尚未接入 OneKuku 内嵌 ADB 配对；激活 = 打开官方 Shizuku。

## 缺口与修复

| 2.0.9 | 修复前 OneLink | 现况 |
|---|---|---|
| `openShizukuApp` 0/1/2 + 跳商店 | 仅 Boolean，未装无商店 | 恢复 `openShizukuApp` |
| 无 CONNECTING 卡死 | 打开前设 CONNECTING | 改 IDLE |
| 已授权则 wake | 只 publish | 对齐 wake + sleepIfEnabled |
| 文案指向 Shizuku | 部分仍 OneKuku prep | onelink overlay 补齐 |

## 取其精华去其糟粕（新 UI）

- **精华**：现 StatusHero / 快捷栏 UI；2.0.9「点激活 → 开 Shizuku」
- **糟粕**：OneLink 未配对时误弹 `home_adb_prep` 三步说明（因 stub `hasPairedOnce=false`）
- **修复**：`HomeScreen` 在 `usesShizuku` 时直接 `onActivateOneKuku`；`beginWirelessPairGuide` / `startCoreFromPrepCard` 分流到 `prepareOneLinkShizukuChannel`

## 验证

- `compileOnelinkDebugKotlin` / `compileOnekukuDebugKotlin`
