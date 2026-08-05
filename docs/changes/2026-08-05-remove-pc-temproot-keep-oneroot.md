# 去掉 PC-TempRoot 测包，保留 OneRoot 一键启动

## 变更
- **删除**（含远端）：`PC-TempRoot-Lite/`、`PC-TempRoot-UI/`、`PC-TempRoot选哪个.txt`
- **保留** OneRoot：`scripts/OneRoot.ps1`、`tools/oneso` hub、根目录启动器
- 根目录一键启动：
  - `OneRoot.bat`
  - `一键启动OneRoot.cmd`（中文别名，转调同一入口）

## 验证
- 本地无 `PC-TempRoot-*` 目录
- `OneRoot.bat` / `一键启动OneRoot.cmd` / `scripts/OneRoot.ps1` 存在
