# 2026-08-05 · OneRoot 本地完全体独立文件夹

## 变更
- `tools/oneso` **迁入**仓库根 `OneRoot/`（Python hub + 工厂 CLI 同目录）
- `tools/oneso` 改为 **junction** → `OneRoot/`，旧命令 `python tools/oneso/oneso.py …` 仍可用
- 独立启动：`OneRoot/一键启动.cmd`、`OneRoot/OneRoot.ps1`
- 仓库根 `OneRoot.bat` / `scripts/OneRoot.ps1` 转发到该目录

## 验收
- `OneRoot/oneso.py`、`hub.py`、`web/` 存在
- `tools/oneso` 为 junction 且可读 `oneso.py`
- 公开仓仍只发 Lite/UI 两个 ZIP（不受影响）