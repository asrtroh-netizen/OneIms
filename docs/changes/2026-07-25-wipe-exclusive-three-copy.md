# 变更说明 · 抹除三项独家产品文案并清理旧包

## 背景

三项显示类能力已迁至 OneTools「特色功能」。本轮按产品要求：

1. 删除仓库根目录非 `3.0.5` 的历史 APK  
2. 抹掉 OneIMS 对外/对内产品文案中对三项的宣传  
3. 收敛独家页死接线（QS Tile 注销、Experimental UI 模型与 MainActivity 回调）

## 改动摘要

- 根目录仅保留 4 枚 `3.0.5` APK（OneKuku / Lite × release/debug）
- `README.md` / `USAGE.md` / 会员文案 / 定价草稿去掉信号格、5G 显示增强、控制中心切卡
- `AndroidManifest` 移除 OneIMS `DataSimSwitchTileService`
- `ExperimentalUiState` / `ExperimentalActions` / `MainActivity` 去掉三项入口接线
- OneKuku 快照摘要不再展示「5G 显示增强」行

## 未做（明确 OUT）

- OneIMS 内部 Manager / 开机重放路径仍可能静默存在（R2 收敛）
- GitHub Release 需另步上传（tag `v3.0.5` 已可推送）
