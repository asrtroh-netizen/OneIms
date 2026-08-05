# 便携 PC 临时 Root（无 Python）

## 动机
Python / oneso Hub / 本机 SDK PATH 对「只想临时 Root」过重。需要一个可拷走的文件夹：自带 adb + so，双击 CMD 即可。

## 交付
- 新目录 `pc-temp-root/`
  - `一键临时Root.cmd`：入口（`dry` 只探测）
  - `adb/`：`adb.exe` + 必要 dll
  - `so/`：从 `app/src/main/assets/temproot` 集成的 `preload*.so` + `catalog.json`
  - `sh/kill-stuck.sh`：对齐 `TempRootShellCommands.KILL_STUCK_PRELOAD`
  - `使用说明.txt`

## 行为
对齐既有链路：`push → kill → LD_PRELOAD×4 → su 验活`。  
按 `ro.product.device` + `ro.build.id` 选 `preload-<device>-<build>.so`，否则回落 `preload-comet.so`。

## 非目标
- 不替代 `OneRoot.bat` / oneso Hub（仍可用于打包与 GitHub so）
- 不做运营商持久化、不恢复手机端一键入口
