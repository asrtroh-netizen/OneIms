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

## 0.8.9 跟进

| 项 | 说明 |
|---|---|
| 长胶囊宽度 | 恢复经典扁岛量级（`168×scale`，夹在 140～360dp），不再近全屏 |
| 扁岛手势 | Overlay 触控一律消费，避免下滑被系统当成拉开通知栏 |

## 0.8.10 跟进

| 项 | 说明 |
|---|---|
| 挖孔位手势 | PILL 悬浮窗宽改为贴合胶囊（非顶栏 `MATCH_PARENT`），把状态栏其余区域还给 SystemUI；展开卡仍全宽 |
| 手势排除 | API 29+ 对岛根视图设置 `systemGestureExclusionRects` |

根因：对齐摄像头时整宽透明窗压在状态栏手势带上，仅 `onTouch` 吞事件不够——事件可能根本进不来或与 shade 竞争；挪到摄像头下方（`BELOW`）不易复现。

## 0.8.11 跟进

| 项 | 说明 |
|---|---|
| 刚下单不出岛 | 放宽美团阶段关键词（支付/已提交/等待商家等）；非 ongoing 的「订单已/成功/等待」也受理；有 ETA 即认配送 |
| 子包包名 | `LiveStatusSource.fromPackage` 支持 `com.sankuai.meituan*` 前缀 |
| 通知文本 | Listener 合并 ticker / summary / info，缓解自定义 RemoteViews 空 EXTRA_TEXT |

## 0.8.12 跟进

| 项 | 说明 |
|---|---|
| 总开关默认 | `masterEnabled` 默认改为 true；实验室打开胶囊时联锁打开总开关 |
| 滴滴刚叫车 | 放宽叫车/派单/快车等文案；非 ongoing 也可出岛 |
| 滴滴包名 | `com.sdu.didi*` 前缀匹配 |
| 通知栏进度 | 仍为设计：不在状态栏旁挂 Live Update 芯片，进度只在 One Capsule |

## 版本

`0.8.12` / `versionCode 43`

## 验证

```text
.\gradlew.bat :onetools:testDebugUnitTest --tests "com.onetools.app.live.capsule.*" --tests "com.onetools.app.live.adapter.*"
.\gradlew.bat :onetools:assembleDebug
```

真机：避摄=对齐挖孔时，在岛上左滑/双击不应拉开通知栏。真实美团/滴滴通知回放仍需通知使用权 + 悬浮窗。
