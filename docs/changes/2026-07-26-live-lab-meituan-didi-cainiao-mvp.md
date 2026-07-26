# 变更说明 · Live Lab 第一批：美团 / 滴滴 / 菜鸟 → 状态栏芯片 MVP

## 范围

- 通知监听白名单：`com.sankuai.meituan*`、`com.sdu.didi.psnger`、`com.cainiao.wireless`
- Live Lab：总开关、三源开关、通知使用权引导、芯片预览
- 发布：复用 Meter 同款 Live Update 芯片请求（API 36+）；低版本常驻通知
- **未打 APK**（用户此前要求暂缓）

## 验证

```text
.\gradlew :onetools:compileDebugKotlin :onetools:testDebugUnitTest --tests com.onetools.app.live.LiveStatusParserTest
```

真机：开通知使用权 → Live Lab 启用 → 触发三 App 进度通知 → 看芯片/常驻通知。
