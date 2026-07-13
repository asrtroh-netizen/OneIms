# 变更说明 · 能力页合并「应用 5G」到「应用核心能力」

日期：2026-07-13  
范围：CapabilitiesScreen / MainActivity / UiModels / 中英文案 / README·USAGE

## 做了什么

1. 能力页「运营商能力与 5G」区块只保留一枚主按钮【应用核心能力】，删除【应用 5G】。
2. `onApplyCore` 依次写入：VoLTE/VoWiFi/VoNR（`applyAll`）→ 5G NR（`apply5g`）→ 信号强度阈值（原 `onApply5g` 逻辑）。
3. 提示与文档中的「点【应用 5G】」统一改为「点【应用核心能力】」；移除未再使用的 `action_apply_5g` 字符串与 `onApply5g` 契约。

## 未改动

- 五开关本身（VoLTE / VoWiFi / VoNR / 5G NR / 信号强度）
- WFC 模式独立应用按钮
- 增强能力 / 高级选项等其它应用入口
