# 变更说明 · Live / Updates 区块容器分割

## 需求

用户指出正在用 Live（实时动态实验室）与 Update 页，区块黏连；并明确**暂不生成 APK**。

## 改动

- `LiveLabScreen`：范围提示 InlineNotice + 空态 Section 卡片
- `UpdatesScreen`：说明 / 定时检查 / 操作 三大 Section；banner 用 InlineNotice；应用卡加描边 elevation；块间距 20dp
- 验证：仅 `:onetools:compileDebugKotlin`（未打 APK）
