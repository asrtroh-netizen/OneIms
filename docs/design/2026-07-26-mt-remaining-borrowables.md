# MT（Material Capsule）剩余可借鉴清单 · OneTools

对照：`.tmp_material_capsule_v155/mt_ui_study_kit/` + `OneCapsule/_archive` + 当前 `onetools/.../live/capsule/`。

## 已借鉴（不要重复挖）

| 项 | OneTools 落点 |
|---|---|
| 挖孔锚定 / 展开下挂 | `CameraAnchor` / `CameraAwareCapsuleLayout` |
| 避摄 BELOW / CENTER + 双叶缝 | prefs + Overlay |
| 展开弹簧时长/贝塞尔 | `CapsuleMotion` |
| 多会话左右切 / 上下展开 | `OneCapsuleStore` + Overlay 手势 |
| 美团/滴滴/菜鸟模板卡 | `OneCapsuleTemplates` |

## 高收益 · 建议下一波（P0）

| # | 能力 | 学哪里（心智） | OneTools 落地建议 | 风险 |
|---|---|---|---|---|
| 1 | **厂商适配器体系** | OneCapsule `adapter/ride|delivery|logistics` | `MeituanAdapter`/`DidiAdapter` 契约：通知→结构化字段（ETA/阶段/车牌） | 低 |
| 2 | **生命周期状态机** | `core/lifecycle` / MT Notification→UI | 会话超时、通知撤→淡出、重连保活 | 低 |
| 3 | **触控热区 EXTRA_TOUCH** | MT `capsuleui` / Const EXTRA_TOUCH_AREA | 扁胶囊周围不可见扩大触摸，防点不中 | 低 |
| 4 | **胶囊设置信息架构** | MT `capsulesettings` | Live Lab 拆「显示 / 避摄 / 动效 / 来源」分组 | 低 |
| 5 | **内容槽位 API** | MT `card/capsules` | `CapsuleContentSlot`：icon / primary / secondary / progress / actions | 中（重构） |

## 中收益 · 有空再做（P1）

| # | 能力 | 说明 |
|---|---|---|
| 6 | DOT / MINI / COMPACT 多档高度 | **产品否决（0.8.1 已回退）**：不照搬 MT 四级壳，保持 PILL↔EXPANDED |
| 7 | ~~主题 token / 动态色~~ | **0.7.1 已做** `CapsuleThemeColors` + Live Lab 开关 |
| 8 | ~~震动反馈节奏~~ | **0.7.1 已做** `CapsuleHaptics` |
| 9 | ~~手势可配置表~~ | **0.7.1 已做** `CapsuleGestureMap` + Live Lab 映射 |
| 10 | ~~预览/校准页~~ | **0.7.1 已做** 挖孔校准滑条（精简版，非独立页） |

## 低优先 / 刻意不做（P2 / 禁区）

| 项 | 原因 |
|---|---|
| 通用卡片商店 / addcards 全家桶 | 爆炸半径大，偏离 OneTools 实时状态 |
| UnlockPro / Play Billing / 改包签名 | **禁止** |
| Accessibility 顶栏全家桶 | 权限重；Overlay 路径已够 MVP |
| QUERY_ALL_PACKAGES 大权限套餐 | 商店/隐私风险 |
| 原样图标/商标文案 | 商用合规；只学结构 |

## 推荐下一刀顺序

1. **厂商适配器**（解析变准 → 岛才真有用）  
2. **触控热区**（手感立刻好一截）  
3. **DOT/MINI 档**（更像海报轻提醒）  
4. **设置信息架构整理**（Live Lab 不乱）
