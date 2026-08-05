# 纠正：UI 版恢复 PowerShell，Lite 保持纯 CMD

## 背景
用户澄清「不要 PowerShell」仅针对简易版；UI 版应继续用 PowerShell 窗体。

## 变更
- 恢复 `PC-TempRoot-UI/ui/TempRoot-UI.ps1`（进度+监测+OneIMS 推荐+赞赏码）
- `PC-TempRoot-UI/一键临时Root.cmd` 重新拉起 PowerShell UI
- `PC-TempRoot-Lite` 仍为纯 CMD，不调用 `powershell.exe`

## 验证
- UI：`-DryRun -AutoClose` → DRY_OK
- Lite：`dry nopause` → EXIT=0；cmd 内无 `powershell.exe`
