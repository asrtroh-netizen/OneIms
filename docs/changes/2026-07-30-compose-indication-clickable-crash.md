# 2026-07-30 · Compose clickable / Indication 崩溃

## 现象

```
java.lang.IllegalArgumentException: clickable only supports IndicationNodeFactory
instances provided to LocalIndication, but Indication was provided instead.
```

## 根因

Compose Foundation 对**不带 Indication 参数**的 `clickable` / `selectable` /
`combinedClickable` / `toggleable` 要求 `LocalIndication` 必须是
`IndicationNodeFactory`。裸 `Modifier.clickable(onClick = …)` 在 LocalIndication
仍为旧 `Indication`（或未对齐的 Material ripple）时直接崩溃。

## 修复

1. 主题层：`OneImsTheme` / `OneToolsTheme` / `OneBatteryTheme` 显式
   `LocalIndication provides ripple()`。
2. 调用点：OneKuku `QuickTile`、OneTools 信息卡改为 `Surface(onClick)`；
   APN 行 / Sponsor 二维码 / Battery 会话行改为显式 `indication = ripple(...)`。

## 验证

- `./gradlew :app:compileOnekukuReleaseKotlin :onetools:compileReleaseKotlin :onebattery:compileReleaseKotlin`
- 真机：点 OneKuku 首页四格、设置行、赞赏码长按（人工）。
