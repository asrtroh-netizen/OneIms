# 根目录 OneRoot 一键运行

## 变更
- 仓库根新增 `OneRoot.bat`：双击或在根目录执行即可启动 PC 单窗临时 Root（转发 `scripts\OneRoot.ps1` → `oneso.py hub`）
- `tools/oneso/README.md` 补充根目录入口说明

## 使用
```bat
OneRoot.bat
```
或双击资源管理器中的 `OneRoot.bat`。

等价于：
```powershell
.\scripts\OneRoot.ps1
```

## 边界
- 不改 exploit / catalog / Hub 业务逻辑
- 不恢复手机端「一键临时 Root」入口
- CLI 探测仍用 `.\scripts\temp-root-pc.ps1`（默认 dry-run，显式 `-Run` 才执行）
