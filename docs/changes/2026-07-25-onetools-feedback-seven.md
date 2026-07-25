# OneTools 0.2.4 · 七项反馈连修

日期：2026-07-25  
模块：`:onetools`  
版本：`0.2.4`（versionCode 6）

## 用户诉求与落地

| # | 诉求 | 落地 |
|---|---|---|
| 1 | 状态栏/QS 网速黑块 | `MeterDynamicIcon.createSilhouette` 作 `smallIcon`；彩色 bitmap 仅作 `largeIcon` |
| 2 | 「更多」→「设置」；更新进 Dock | `ToolsDestination`：`UPDATES` + `SETTINGS`；去掉嵌套 More→Updates |
| 3 | 各页大标题 OneXxx | OneCaller / OneMeter / OneBattery / OneUpdate / OneAudio |
| 4 | 通道卡名 OneLink | `channel_display_name` → OneLink |
| 5 | 录音球真实反馈 | 悬浮球录音中显示 `mm:ss` 计时 |
| 6 | 通话轻量、去拦截 UI | Dock「通话」改挂 `CallLiteScreen`（归属试查 + OneAudio） |
| 7 | 通话记录勿用归属当地名 | Directory：`DISPLAY_NAME`=号码；`LABEL`=归属；忽略骚扰库顶替姓名 |

## 验证

- `:onetools:testDebugUnitTest` PASS
- `:onetools:assembleDebug` PASS
- 本地 APK：`onetools/dist/OneTools-latest-debug.apk`

## 设备侧建议点验

1. 通知栏小图标是否仍为黑块（应见白色速度字形）
2. Dock 是否出现「更新」「设置」
3. 未存联系人来电/通话记录：姓名行是号码，标签行是「省·市·运营商」
4. 接通后点录音球：变色 + 计时递增
