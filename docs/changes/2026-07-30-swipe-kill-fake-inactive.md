# 2026-07-30 · 划掉后台假「未激活」（小米/一加，Pixel 正常）

## 现象

国产机（小米、一加）划掉后台再开，首页状态框回到「还差一步 / 未激活」；Pixel 上通常没事。一加诊断见「已激活」下一秒「请先激活」。

## 根因（叠加）

1. **脏 hint**：`bootUiHint=NEEDS_ACTIVATION` 时 `bootForceInactive` 强制卡片未激活；1s 轮询还会把 store 里的脏 hint 刷回 UI，即使 binder 已就绪。
2. **OneLink 冷启 FGS 崩**：未配对时 `LaunchedEffect` 误 `enqueue(OneKukuBootRestoreService)`，小米上 `ForegroundServiceDidNotStartInTimeException` 杀进程 → 永远像未激活。
3. 前台复连已有，但若进程被 FGS 崩掉，复连来不及生效。

## 修复

- `settle` / 轮询 / `ON_RESUME`：桥就绪则清 `NEEDS_ACTIVATION`，禁止脏 hint 覆盖。
- OneLink：禁止首页 enqueue BootRestore FGS；复连只靠 Shizuku binder 轮询。
- `OneKukuBootRestoreService.ensureForeground`：成功后再置 flag，失败可重试 + 无 type 兜底。

## 验证

- 双 flavor 编译
- adb：`am force-stop` → monkey 冷启 → 查 session.log 无 FGS crash；OneKuku 见 `foreground reconnect` 后应能再激活（onebridge 仍在时）
