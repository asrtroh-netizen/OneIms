# 双通道状态卡四态：未激活 / 激活中 / 就绪 / 休眠

日期：2026-07-16

## 需求

1. OneKuku / OneLink 状态卡统一四态；关 App → 休眠，再开 → 就绪；划掉后台不要求重配对。
2. 「就绪 / 休眠」只显示在右上大胶囊，不缀在通道名后。
3. 排障「只读检查」四项保持一级 ActionGrid（恢复 HEAD 布局，撤销弹窗收纳）。

## 实现

- `OneKukuCardState`：INACTIVE / ACTIVATING / READY / SLEEPING
- `StatusHero`：就绪/休眠标题用 `channel_display_name`；胶囊显示使用状态
- `MainActivity`：`ON_STOP` → `sleep`，`ON_START` → `wake`；收尾 `markActive`（前台就绪）
- `DiagnosticsScreen`：恢复一级四项只读检查
- 文案：进度条「激活中 / 就绪 / 休眠」；OneLink branding 同步

## 验证

- 单测：`OneKukuCardPolicyTest`（Gradle 不可用时 NOT RUN）
- 真机：激活后胶囊「就绪」→ 划掉/回桌面胶囊应「休眠」→ 再开回「就绪」且无需重授权
