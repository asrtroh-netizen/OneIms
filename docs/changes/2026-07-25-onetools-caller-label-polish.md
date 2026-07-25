# 2026-07-25 · Caller 归属打磨

## 变更

- 新增规则类型 `CallRuleKind.LABEL`：只打拨号器/来电归属标签，**不拒接**
- 添加规则 UI：归属 / 拦截 / 白名单三选一；归属必须填标签名
- 试查结果改为中文（将拦截 / 正常接通 / 显示「xxx」）
- 规则列表中文化；样例 JSON 含归属条
- Directory 对 LABEL 也能回传显示名

## 验证

```text
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```
