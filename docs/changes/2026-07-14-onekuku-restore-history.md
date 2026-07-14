# 变更说明：首页恢复记录摘要

**日期**：2026-07-14  
**范围**：`OneKukuRestoreHistoryStore` + Executor 落库 + 四宫格 History 弹层摘要；不改外壳。

## 展示

最近恢复（相对时间）/ 结果 / OneKuku 状态 / 目标卡 · 运营商 / 分项成功失败跳过；无终端与敏感明文。

## 验证

- compileDebugKotlin PASS
- 真机弹层 NOT RUN
