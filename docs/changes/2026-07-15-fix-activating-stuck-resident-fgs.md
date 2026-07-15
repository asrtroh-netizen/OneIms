# 2.1.7 · 修「激活中」卡住 + OneKuku 后台常驻保活

## 现象

- 点进首页常显示「激活中」，像卡死
- 期望：连过一次 Wi‑Fi 后通道顶在后台，重进不要又走激活中

## 根因

`prepareOneKukuCore` 在已配对路径上**先** `setPhase(CONNECTING)` 再 `wake()`，UI 立刻变激活中；wake/直连慢或挂起时相位不收敛。

## 修复

1. 已配对：先静默 wake；成功直接 IDLE + 拉起 `OneKukuResidentService`；仅 wake 失败才进 CONNECTING
2. 成功相位统一清回 IDLE（卡片走 resolve→READY）
3. 新增 `OneKukuResidentService`：FGS 常驻，20s 心跳，掉线且有 Wi‑Fi 时静默重连（不刷激活中）
4. 激活中相位 45s 超时 → FAILED / 若已就绪则 IDLE

## 版本

- `2.1.7` / `versionCode=52`
