# 2026-07-25 · Caller UI 精致化（对标 Telo 观感）

## 目标

Pixel Telo 已有功能；OneCaller **干净室**在 Pixel 上做得更精致好看，不抄 Telo 源码。

## 变更

- Hero 状态卡：一眼看出是否已是默认来电筛选
- 分段按钮：归属 / 拦截 / 白名单
- FilterChip：前缀 / 标签组
- 分区 Surface：设置、添加、试查、导入
- 规则卡片：彩色 kind 徽章 + 层级排版
- 空态引导文案

## 验证

```text
./gradlew :onetools:compileDebugKotlin :onetools:testDebugUnitTest
```
