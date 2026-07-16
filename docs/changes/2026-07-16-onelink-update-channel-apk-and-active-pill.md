# 2026-07-16 · OneLink 更新分流 + Active 胶囊

## 问题

1. 应用内更新从 Release 取「第一个 .apk」→ OneLink 误下 OneKuku 包
2. 就绪/休眠文案互相掺「按需休眠」
3. 胶囊位置与「就绪」措辞不符合预期

## 修复

- `UpdateChecker`：按 `ChannelLine` 匹配 OneKuku/standalone vs OneLink/Shizuku 资产
- `StatusHero`：胶囊紧跟大字标题后；就绪胶囊文案 **Active**
- 就绪/休眠副文案拆清：前台 Active / 后台休眠，再开唤醒不重激活
