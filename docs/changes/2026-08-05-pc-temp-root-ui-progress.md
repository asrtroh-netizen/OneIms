# pc-temp-root：进度窗 + 设备监测

## 变更
- 双击 `一键临时Root.cmd` 默认打开 WinForms UI（系统自带 PowerShell，仍无 Python）
- 显示进度条、阶段、Serial/机型/Build/SELinux/轮次、本步耗时、实时日志
- LD_PRELOAD 长等待每 5s 心跳刷新进度文案
- 保留 `console` / `dry` 黑窗回退

## 验证
- UI `-DryRun -AutoClose` → `DRY_OK device=comet build=CP2A.260705.006`
- `一键临时Root.cmd console dry nopause` → EXIT=0
