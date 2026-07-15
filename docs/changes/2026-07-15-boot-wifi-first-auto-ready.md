# 开机：Wi‑Fi 前置 + 已配对全自动就绪

## 动机

用户反馈 Wi‑Fi 校验后置、范围麻烦；期望对齐「开机后不用点，后台激活就绪并恢复关机前配置；只有新网/未配对才手动」。

## 行为

1. **已配对**：开机先等 STA（最长 60s）→ 静默开无线调试 → 无码直连 → 重放能力配置 / 恢复快照。
2. **Wi‑Fi 晚到**：写 `WAITING_WIFI`（不钉红卡）、清本开机占位；`NETWORK_STATE_CHANGED` 连上后再跑。
3. **从未配对 / 真需填码**：才挂六位码通知 + `NEEDS_ACTIVATION`。
4. **activate 内**：已配对路径把 Wi‑Fi 等待挪到 mDNS **之前**。

## 改动文件

- `OneKukuAdbMdns.kt`：公开 `waitForWifiClient`
- `OneKukuEmbeddedAdbActivator.kt`：Wi‑Fi 前置
- `OneKukuBootRestoreCoordinator.kt` / `Store`：`WAITING_WIFI` + 可再试
- `BootReceiver.kt` + Manifest：监听 Wi‑Fi 状态
- `MainActivity.kt` + strings：等待 Wi‑Fi 文案与执行中态

## 验证

- `:app:compileDebugKotlin`
- 真机：已配对 + 记住 Wi‑Fi 冷开机应无手点（NOT RUN 直至装包）
