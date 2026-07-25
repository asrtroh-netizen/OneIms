# 2026-07-25 · OneBlock → 规则 + onespam 双写灌库

## 问题

原先「从 OneBlock 灌入离线库」只把 **EXACT BLOCK** 写入 `onespam.db`。  
而线上/样例 `one-blocklist.json` 早期几乎全是 **PREFIX**，导致 onespam **灌入 0 条**。

## 修复

- `OneBlockImporter.importJson`：全量规则 → `CallRuleStore`；精确拦截号 → `onespam.db`
- Caller UI：CDN 批量灌库 / 文件导入 / onespam 区按钮统一走双写
- 样例库扩充：`docs/product/samples/one-blocklist.json`（含精确号，可供 onespam）
- 构建产物：`onetools/scripts/build-onespam-pack.py` → `onetools/cdn/caller/{onespam_*.db,zip,spam-sync.json}`

## 发布

```powershell
python onetools/scripts/build-onespam-pack.py
powershell -File onetools/scripts/publish-blocklist.ps1 -JsonPath docs/product/samples/one-blocklist.json
# 另将 onetools/cdn/caller/onespam_*.zip 与 spam-sync.json 上传到 CDN
```

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
