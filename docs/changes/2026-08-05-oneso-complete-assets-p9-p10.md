# OneSo-assets：补齐 P9/P10 系列 so

## 缺口（修复前）

| build | 系列 | 缺口 | 补法 |
|---|---|---|---|
| `CP2A.260605.012` | P9 | `komodo` | 同构建 tokay/caiman/comet **字节相同** → 复制 |
| `CP2A.260605.012.C1` | P9 | `caiman` | 同构建 tokay/komodo/comet **字节相同** → 复制 |
| P10 各档（0305→0605） | P10 | 无 | 四机齐全 |
| `CP2A.260705.006` | P9 | 无 | 四机齐全（label retarget） |

说明：0605 P9 共用 `p9_cp2a_260605_012_truephone`，无 per-device label；0705 才有 `*_cp2a_260705_006`。

## 工具

新增 `python tools/oneso/oneso.py complete-assets`：

- 安全补齐可复制/可 label 的 P9 缺口
- 重写 `OneSo-assets/catalog.json` + `SHA256SUMS`
- P10 缺口只报告、不伪造

## 验证

- `complete-assets` → created=2；catalog entries=36；二次 dry-run created=0
- `pack-0705` → ok=4/4
- `pack-p10` → ok=4/4

## 0705 / 0805

| Build | 状态 |
|---|---|
| `CP2A.260705.006` P9 | **齐套** 4/4（assets + OneIMS offline） |
| `CP2A.260705.006` P10 | **无成品**（禁止用 P9 so 交叉） |
| `CP2A.260805.*`（字面 0805） | **不存在**：assets/factory/TEMP/真机均无；真机仍 `CP2A.260705.006` |
