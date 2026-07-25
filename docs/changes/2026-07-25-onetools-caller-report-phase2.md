# 2026-07-25 · OneCaller Phase-2 社区回灌脚本

## 摘要

落地最省钱 Phase-2：App 手动导出 `onetools.report.v1` + `ingest-reports.py` 聚合门槛 → OneBlock 候选/合并；不上商业 API，社区开关默认关。

## 交付

| 项 | 说明 |
|---|---|
| `ReportExport.kt` | 导出契约；不上传明文 note |
| `CallerPrefs` | `communityReportOptIn` 默认关 + opaque `reportClientId` |
| `CallerScreen` | 开关 +「导出社区举报 JSON」 |
| `ReportTag.WRONG_TAG` | 纠错标签；本机不自动 BLOCK |
| `ingest-reports.py` | 门槛≥3 / 反刷 50/日 / 白名单 / demote |
| 样例 reports | `docs/product/samples/caller-reports/` |
| Workflow | `.github/workflows/ingest-caller-reports.yml`（手动） |

## 用法

```bash
# 干跑：只写 candidates
python onetools/scripts/ingest-reports.py docs/product/samples/caller-reports

# 合并进 sample blocklist 并 bump 日期
python onetools/scripts/ingest-reports.py docs/product/samples/caller-reports --apply --bump-date

# 重建 onespam 包
python onetools/scripts/build-onespam-pack.py docs/product/samples/one-blocklist.json
```

## 注意

`expand-oneblock-spam.py` 会整表重写 sample；社区合并条目若需长期保留，应并入 expand 种子或改 expand 保留 `source=community-report`。
