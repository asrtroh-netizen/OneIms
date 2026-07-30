> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · P3a：`:care-min` 模块骨架

## 做了什么

- 新增 Android library `:care-min`：宿主契约常量 + CARE_MIN boot shell（`onekuku_server` / `ShizukuService`）
- `settings.gradle.kts` include
- **尚未**把邻仓完整 server 源码/依赖闭包打进模块（下一步）
- 默认 `CHANNEL_ENGINE` 仍为 `ONEBRIDGE`（行为不变）

## 验证

```bat
./gradlew :care-min:compileDebugKotlin
```

