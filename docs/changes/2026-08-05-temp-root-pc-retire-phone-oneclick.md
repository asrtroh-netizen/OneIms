# 临时 Root：撤手机一键入口 → PC 按需脚本

## 动机

手机端「一键临时 Root」在 OneKuku / Lite 上会长时间占用（LD_PRELOAD 多轮 + 杀残留），易卡死界面；SELinux / mm_leak / Shizuku newProcess 等问题也不适合当产品入口。

## 行为变更

| 端 | 变更 |
|---|---|
| OneKuku + Lite 首页 | `showTempRootExperiment = false`，**不再展示**一键临时 Root 卡片 |
| Root 功能区 | 不变：仍仅在有可用 Root 时显示（开机自启 / 应用运营商与我的配置 / 工具） |
| PC | 新增按需脚本；**默认 dry-run**，显式 `-Run` / `--run` 才执行 exploit |

## 入口

```powershell
.\scripts\temp-root-pc.ps1              # 探测机型 + 匹配 so + 打印计划
.\scripts\temp-root-pc.ps1 -Run         # push + kill + LD_PRELOAD×N + su 验活
python tools/oneso/oneso.py temp-root --run --so PATH\to\preload.so
```

流程对齐 `TempRootShellCommands`：`adb push` → `KILL_STUCK_PRELOAD` → `LD_PRELOAD=…/preload-comet.so /system/bin/id` → `/data/local/tmp/su` 与 apex `su` 验 `uid=0`。

## 版本

- `3.2.8` / `versionCode=89`

## 非目标

- 不删除 Kotlin Activator 实现（便于以后调试复用），只藏 UI。
- 不保证任意机型一点成功；仍依赖 catalog 匹配 so + exploit 本身。
