# One Capsule · 架构（对齐概念图）

## 目标
把「One Capsule / Material 实时服务岛」做成可真用的悬浮岛：轻提醒扁胶囊、展开进度卡/详情卡、多任务左右切换，数据来自白名单通知。

## 分层
| 层 | 职责 | 路径 |
|---|---|---|
| Models / Templates | 会话、阶段、展开模板、演示数据 | `live/capsule/OneCapsuleModels.kt` `OneCapsuleTemplates.kt` |
| Store | 多会话队列、展示模式、手势驱动的切换 | `OneCapsuleStore.kt` |
| Overlay | WindowManager 扁胶囊 + 展开卡 + 手势 | `OneCapsuleOverlay.kt` |
| Hub | 通知芯片 + Store/Overlay 编排 | `LiveStatusHub.kt` |
| Listener / Parser | 白名单通知 → 结构化会话 | `LiveStatusNotificationListener.kt` `LiveStatusParser.kt` |
| Live Lab UI | 开关、调节、四类预览 | `LiveLabScreen.kt` |

## 状态
`HIDDEN` → `PILL`（轻提醒）→ `EXPANDED`（进度卡 / 详情卡）

## 手势
- 下滑 / 点按：展开
- 上滑 / 再点按：收起
- 左右滑：切会话

## 边界（诚实）
悬浮窗无法嵌入 SystemUI，也不能真正挖空摄像头；默认 Y 在状态栏下方避摄，观感为「高度仿真」而非官方岛。
