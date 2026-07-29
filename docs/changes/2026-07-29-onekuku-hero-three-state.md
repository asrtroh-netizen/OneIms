# 2026-07-29 · OneKuku / OneLink Hero 三态去休眠

## 决策

对齐 V15：首页通道卡 **未激活 / 激活中 / 就绪**；去掉用户可见「休眠」。

## 行为边界

- 内部 `OneKukuRunnerState.SLEEPING` 可保留（退后台书签 / 停旧 FGS）。
- 对外 `OneKukuCardPolicy.resolve`：已授权一律 `READY`。
- 阶段条 3 段；设置「自动休眠」文案改为「用完不拉 App 常驻」。
- **不 Push**（等用户命令）。

## 验证

- `compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin`
- `OneKukuCardPolicyTest`
