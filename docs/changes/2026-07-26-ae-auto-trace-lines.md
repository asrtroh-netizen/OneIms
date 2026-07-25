# AE 自动描线管线（reference.png → TRACE_LINES）

日期：2026-07-26

## 背景

客户原图为宽幅波浪脊线纹理。需求是用 Python/OpenCV 从真实像素脊线中提取约 10 根代表性长线，再通过 After Effects JSX 写入当前活动合成，生成可手动编辑的贝塞尔路径——不做网页、不手工描线。

## 交付物

| 文件 | 作用 |
|---|---|
| `reference.png` | 客户原图（由附件落入项目根） |
| `analyze_lines.py` | 结构张量取向 + 脊线流线积分；跨度/效率筛选约 10 条；输出 `paths.json` 与预览 |
| `paths.json` | 顶点 + in/out 切线（图片像素坐标） |
| `apply_paths.jsx` | 读 JSON，按选中参考层 `sourcePointToComp` 变换，重建 `TRACE_LINES` |
| `run_trace.ps1` | 查找最高版本 `AfterFX.exe`，以 `-r` 执行 JSX |
| `trace_preview.png` | 提取结果叠加预览 |

## 关键行为

1. 流线沿真实脊线方向积分，短暂弱响应允许跨过景深模糊带。
2. 以 **跨度 (span)** 与 **效率 (span/length)** 淘汰来回抖动的虚长碎线。
3. JSX：若已存在 `TRACE_LINES` 则先删再建；约 10 个独立组 `Line 01…`；开放路径；白描边 2px；圆帽/圆连；无填充；不改动其他图层。

## 验证

- `python analyze_lines.py` → `quality: PASS`，10 条，span 约 2.9k–5.5k px
- `powershell -File run_trace.ps1` → AfterFX exit 0
- `ae_apply_result.txt` → `OK / TRACE_LINES created with 10 editable open paths`
