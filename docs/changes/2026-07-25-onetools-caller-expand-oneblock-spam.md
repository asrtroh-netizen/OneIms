# 2026-07-25 · 扩充 OneBlock 精确/前缀骚扰号 + onespam 最长前缀

## 背景

公开、许可干净的「中国海量精确骚扰号」全集几乎不存在；BanHarassment 无 LICENSE 且默认屏蔽全区号固话，不适合做默认库。

## 做法

1. `expand-oneblock-spam.py`：虚拟号段/高风险 PREFIX + 精确种子号 + 银行 LABEL  
2. onespam DAO：`LIKE phone_number || '%'` **最长前缀命中**（PREFIX 与 EXACT 同库）  
3. 双写灌库：BLOCK 的 EXACT+PREFIX 都进 onespam  
4. 已发布 OneBlock `phone/one-blocklist.json` 与 Release `onespam_*.zip` / `spam-sync.json`

## 规模（本轮）

- blocklist 条目约 **47**
- onespam 行约 **34**（含前缀行；可拦整段虚拟号）

## 风险披露

默认拦截 `170/171/162…` 虚拟号段可能误伤快递/外卖/打车来电 → 用白名单放行。

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
