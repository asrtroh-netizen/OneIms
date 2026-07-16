# 2026-07-16 · OneKuku 回归休眠 + TcpIP 5555

## 动机

常驻前台服务 + 忽略 autoSleep 导致发热/常驻体感差；`tcpip:5555` 曾为护共存被关掉，出门保活变弱。产品要求：**仅 OneKuku 线**恢复休眠（秒级唤醒）并加回 TcpIP；OneLink/Shizuku 不变。

## 改动

1. `OneKukuSleepController` / `OneKukuHiddenRunner`：恢复真实 `SLEEPING`；`sleepIfEnabled` 再认 `ConfigStore`；休眠时 `stop` ResidentService。
2. `ConfigStore.isOneKukuAutoSleep` 默认改回 `true`。
3. 就绪收尾统一 `settleOneKukuChannelAfterReady()`：自动休眠则休眠，否则才起常驻服务；`ChannelLine.usesEmbeddedBridge` 门禁，OneLink 跳过。
4. `persistTcpip5555` 恢复下发 `tcpip:5555` + 回连（仍仅 Embedded 激活路径）。
5. 首页就绪文案/进度段回到「休眠」语义。

## 验证

- `:app:compileOnekukuDebugKotlin`
- `:app:compileOnelinkDebugKotlin`（确认不误伤）
- 打包/装机：按会话禁令 NOT RUN
