# 尝试系统侧辅助 5G-A

日期：2026-07-14

## 结论

可以「加」——但不是伪造状态栏位图，而是：

1. 继续写入全局 `5g_icon_configuration_string`（AOSP：`5G` / `5G_PLUS`）
2. **新增**：一并写入 `nr_advanced_threshold_bandwidth_khz_int=1` + `include_lte_for_nr_advanced_threshold_bandwidth_bool=true`，放宽 NR Advanced 判定
3. 国行 ROM 常在 Advanced 时自绘 **5G-A**；若 ROM 无此资源，状态栏仍可能是 5G+

关闭增强时按首次基线恢复图标串与 Advanced 阈值。

## 边界

- 无法保证所有机型出现「5G-A」二字
- 可能需飞行模式/重启后生效
