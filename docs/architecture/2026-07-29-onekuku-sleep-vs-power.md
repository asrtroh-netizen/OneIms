# 2026-07-29 · OneIMS「休眠」与耗电边界

## 结论（架构）

**哥哥的直觉对：稳态耗电应主要来自 Shizuku / `onebridge_server` 这类特权进程，而不是 OneIMS App 自己的「休眠」标签。**

OneIMS 的「休眠」本质是 **App 侧状态机 + 停掉历史前台常驻**，**不拆桥、不杀特权进程**。

## 证据

| 层 | 谁 | 休眠时发生什么 | 耗电含义 |
|---|---|---|---|
| 特权桥 | OneKuku：`onebridge_server`；OneLink：外部 Shizuku | **继续存活**（可秒级 wake） | **主耗电源**（与官方/V15 同类） |
| App 状态 | `OneKukuHiddenRunner` → `SLEEPING` | 仅翻转枚举 | 几乎为 0 |
| App FGS | `OneKukuResidentService` | `sleep` / `settle` 时 `stop`；`start` 已空操作 | 避免 App 再挂轮询前台（历史上才是发热源） |
| OneLink | Resident 为桩 | 无 App 侧常驻服务 | 耗电几乎只在外部 Shizuku |

代码指针：`OneKukuSleepController`、`MainActivity.sleepChannelWhenBackgrounded` / `settleOneKukuChannelAfterReady`、`docs/changes/2026-07-16-onekuku-sleep-tcpip-restore.md`、邻仓 V15「三态去休眠」`docs/changes/2026-07-17-shizuku-hero-three-state-no-sleep.md`。

## 建议

1. **保留行为**：退后台不拉 App FGS、不拆桥 —— 这是省电正确方向。  
2. **可弱化 UI**：首页四态里的「休眠」对用户价值低、易误解成「通道关了」；可对齐 V15 收敛为 未激活 / 激活中 / 就绪（后台仅内部态）。  
3. **不要为了「少耗电」去杀 `onebridge_server`/Shizuku**：杀了就失去秒级恢复与开机重放，得不偿失。
