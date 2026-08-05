# PC TempRoot：全面纯 CMD（去掉 PowerShell）

## 变更
- 删除 `PC-TempRoot-UI/ui/*.ps1` 与 `console/` 回退目录
- UI 版改为纯 CMD：彩色进度 + `choice` 菜单 + `start` 打开 GitHub/Releases/赞赏码图片
- Lite / UI 均不调用 `powershell.exe`

## 验证
- 包内无 `.ps1`；cmd 无 `powershell.exe` 调用
- `PC-TempRoot-UI\一键临时Root.cmd dry nopause` EXIT=0
- `PC-TempRoot-Lite\一键临时Root.cmd dry nopause` EXIT=0
- `PC-TempRoot-UI\一键临时Root.cmd sponsor nopause` EXIT=0
