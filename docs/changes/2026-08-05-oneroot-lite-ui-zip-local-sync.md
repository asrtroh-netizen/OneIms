# OneRoot Lite/UI zip 与本地同步

日期：2026-08-05  
范围：`release/oneroot-public` → `_zipstage` → `OneRoot-*.zip` → `E:/GQ/One/OneSo-assets/oneroot/`

## 背景

本地展开目录 `release/oneroot-public/oneroot/{Lite,UI}` 已含「清理残留」安全语义（`su-keep` / `su-teardown`），但 `OneRoot-Lite.zip` / `OneRoot-UI.zip` 仍是旧 cleanup 逻辑，与本地漂移。

## 做法

1. 以 `oneroot-public` 为真源，覆盖 `_zipstage/OneRoot-{Lite,UI}`
2. 用 Python 重打带根目录前缀的 zip（23 / 25 文件）
3. 同步到本机 `OneSo-assets/oneroot/*.zip`
4. 刷新 `release/OneRoot-Lite/` 解压镜像

## 验证

| 检查 | 结果 |
|---|---|
| stage vs public | content_diff=0 |
| stage vs zip | content_diff=0 |
| public vs zip | content_diff=0 |
| Lite zip 关键字 | `su-keep` + `TEARDOWN_OK` |
| UI zip 关键字 | `su-keep` + teardown 分支 |
| Lite SHA256 | `2e329adfff4dfc20c23aeb7e8899a28613133f78b9e1778989d1be435ea79ef3` |
| UI SHA256 | `f16af69095fd288db504c730a70df6e35e618b710ebb441adf89e3b9b474f467` |

## 边界

- 本轮默认完成**本机**对齐；是否 `git push` OneSo-assets 远端见交付时仓库状态。
- 未改 Hub Python 源码（此前已对齐语义）。
