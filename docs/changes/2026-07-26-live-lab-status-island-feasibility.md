# 可行性 · Live Lab「国内软件实时状况」上状态栏芯片（灵动岛等价）

## 结论

**现实，能做**——但要分清两层：

| 层 | 结论 |
|---|---|
| 展示面（像灵动岛抬头看） | **可行**。Pixel / Android 16 的 Live Updates 状态栏芯片就是官方等价；OneTools Meter 已跑通同一路径。 |
| 数据面（国内软件实时状况从哪来） | **可行但要选型**。没有通用「读任意 App 内部状态」的合法 API；MVP 应走通知监听 / 明确白名单源。 |

## 与现有模块关系

- **Live Lab**：正确产品入口（空态已预留「实时动态实验」）。
- **Updates**：应用版本更新中心，不适合当实时状态源。
- **Meter / `SpeedMonitorService`**：已实现 `setRequestPromotedOngoing` + `setShortCriticalText`（≤7 字芯片）+ 低版本贴顶悬浮兜底——**展示管道可复用**。

## 推荐 MVP

1. Live Lab 增加「状态源」白名单（先 1～2 个国内 App / 场景）。
2. `NotificationListenerService` 解析其进度类通知 → 归一化状态文本。
3. 复用 Meter 的 Live Update / 贴顶悬浮管道输出芯片。
4. 用户可开关、选显示哪一类状态。

## 需产品拍板

- 第一批盯哪些国内软件、看什么字段（外卖进度？网约车？物流？）
- 是否接受通知使用权（否则数据源很难合法做稳）
- 验收机型：是否仍以 Pixel + Android 16 为主
