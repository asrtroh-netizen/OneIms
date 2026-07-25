# 设备详情卡重设计 + 排障弹出详情（不写日志）

## 需求

1. 设备详情太丑 → 对齐状态卡气质：主次分明、小信息格
2. 排障四项弹出不是确认执行，而是**弹出详情**；**不往日志写**（回到以前好用的做法）

## 落地

- `DeviceSnapshot` + 首页底栏白底 elevation 卡：大标题型号、2×2 chip（系统/芯片/SIM/代理）+ 代号/补丁/策略行
- `DiagnosticsActions` 四项改为 `suspend () -> String`；页内 AlertDialog 展示；`MainActivity` 不再 `runOperation/publish`
- 重应用/导出仍保留确认试操作；日志仍为弹窗查看

## 验证

双 flavor Kotlin compile PASS
