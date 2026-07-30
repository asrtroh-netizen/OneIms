# 2026-07-30 · 独立版与分割版逻辑同构

## 用户原话

> 之前的东西逻辑一律不变；独立版本和组合版本没一点差异，差异就是独立和分割。

## 落点

- 冻结真源：`docs/architecture/2026-07-30-onekuku-mirrors-lite-shizuku.md` §0
- 代码锚点：`ChannelLine` KDoc
- **未改**运行时业务逻辑 / 默认引擎

## 对照

| | 分割（组合） | 独立 |
|---|---|---|
| Flavor | onelink | onekuku |
| 通道 | 外置 Shizuku | 内嵌桥（目标 CARE_MIN） |
| 业务 | Lite | 同一 App |
| 协作步骤 | 同构 | 同构 |
