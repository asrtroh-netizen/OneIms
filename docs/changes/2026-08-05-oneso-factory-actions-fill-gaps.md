# OneSo-factory Actions：补齐 P9 缺口 target

## 做法（对齐既有 CI）

1. 在 `OneSo-factory` 补 thin-wrapper `target.h`（与 `caiman@0605.012` / `komodo@0605.012.C1` 同构）
2. `git push` → `gh workflow run "Build all preload.so"`
3. package 步 seed `prebuilt/0705` + collect → 有 `ASSETS_PUSH_TOKEN` 时推到 `OneSo-assets`

## 本轮新增 target

| PROJECT | 内容 |
|---|---|
| `komodo-CP2A.260605.012` | fingerprint + `#include tokay-CP2A.260605.012` |
| `caiman-CP2A.260605.012.C1` | fingerprint + `#include tokay-CP2A.260605.012` |

本地 commit：`16a590c`（factory）。buildable 计数 **30 → 32**。

## 验证

- `python scripts/list_buildable_projects.py` 含上述两行
- **Actions / push**：本机 `github.com:443` 不通时标 NOT RUN；网络恢复后执行：

```powershell
cd E:\GQ\One\OneSo-factory
git push origin HEAD
gh workflow run "Build all preload.so" --ref main
gh run watch
```

## 仍无法由 Actions 造出

- `CP2A.260805.*`：无 target / 无 OTA 偏移
- P10 @ `CP2A.260705.006`：无源，禁止交叉 P9
