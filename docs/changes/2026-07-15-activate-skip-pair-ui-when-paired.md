# 激活：已配对跳过六位码说明/通知

## 动机

重启后连上已配对 Wi‑Fi，点「激活」仍先弹出三步说明 + 状态栏六位码入口，随后又自动直连成功。体验像「又要配对一次」。

## 交互

1. **已配对过**（`hasPairedOnce`）：点激活 → 直接 `prepareOneKukuCore`，卡片进连接中；优先 `tryEnableAdbWifi`，无权限才打开无线调试页；成功则无码就绪；仅 `NeedPairingCode` 再挂状态栏填码。
2. **从未配对**：保持原流程——说明弹窗 + 状态栏通知 → 确认后激活。

## 改动文件

- `HomeScreen.kt`：INACTIVE/FAILED 入口按 `hasPairedOnce` 分支
- `MainActivity.kt`：`prepareOneKukuCore` 已配对不预先挂六位码通知
- `UiModels.kt`：注释对齐新契约

## 验证

- 静态：改动处调用链回放
- 真机：已配对重启后点激活应无六位码弹窗/通知且能就绪（adb 未连时标 NOT RUN）
