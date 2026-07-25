# 2026-07-25 · 自有 OneBlock 更新加固

## 决策

继续 **自有 OneBlock / onespam** 更新；不绑定 Telo 云 CDN；不买商业查号。

## 改动

- `expand-oneblock-spam.py`：保留 `source=community-report` 精确号，并合并 `caller-report-candidates.json` 的 promote
- `update-oneblock.py`：一键 ingest(可选) → expand → 镜像 assets/cdn
- 产品文档补充更新路径

## 本地用法

```bash
# 日常：扩种子 + 保留社区行 + 重建 onespam
python onetools/scripts/update-oneblock.py

# 先聚合社区举报样例再 expand
python onetools/scripts/update-oneblock.py --ingest

# 仅按当前 sample 重建 zip（不重写种子）
python onetools/scripts/update-oneblock.py --skip-expand
```

发布到 GitHub Release 仍用 `publish-blocklist.ps1` + `gh release upload`（脚本不自动 push）。
