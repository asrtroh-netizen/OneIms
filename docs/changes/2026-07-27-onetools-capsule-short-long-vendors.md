# OneTools 0.8.8 · 短/长胶囊 + 美团/滴滴状态链续作

## 卡点来源

XJ021「真实通知适配」断线前用户四点要求：

1. 文字在容器内自适应，避免卡断  
2. 美团/滴滴：短=logo+时间，左滑长=状态+时间，双击大框；菜鸟无时间只短胶囊  
3. 参考超级岛 / 流体云心智  
4. 除 Meter 外不在状态栏旁挂第二颗小胶囊  

## 本轮落地

| 项 | 落点 |
|---|---|
| 文字自适应 | `OneCapsuleOverlay` 左右文案改为固定宽度 + `AutoSize` |
| 短/长胶囊 | `CapsulePillFace` / `TOGGLE_LONG` / Store.`togglePillSize` |
| 菜鸟取件码 | `CainiaoVendorAdapter` + `extractPickupCode` |
| 美团状态链 | 已下单→已出餐→配送中→已送达；终态优先；无假 ETA |
| 滴滴行程链 | 等待接驾→司机赶来→行程中→已到达；车牌/司机进 detail |
| 去挤位芯片 | `LiveStatusHub` 取消 Live Update 旁路通知 |
| 长胶囊粘性 | 同会话 `upsert` 保留 `pillSize`（通知刷新不打回短态） |
| 手势迁移 | `gesture_schema=2` 一次性覆盖旧单击展开 |

## 版本

`0.8.8` / `versionCode 39`

## 验证

```text
.\gradlew.bat :onetools:testDebugUnitTest --tests "com.onetools.app.live.capsule.*" --tests "com.onetools.app.live.adapter.*"
.\gradlew.bat :onetools:compileDebugKotlin
```

真机真实美团/滴滴通知回放：需通知使用权 + 悬浮窗。
