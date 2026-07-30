# 2026-07-30 · READY 盖过 CONNECTING（消 ACTIVE+激活中横跳）

## 现象

日志 `state=ACTIVE` / binder 已到，Hero 仍「激活中」；划掉后台再开反复横跳。

## 根因

1. `oneKukuState = fromActivationPhase(phase) ?: resolve(ready)`：`CONNECTING` **优先于** `serviceReady`。
2. 前台多拍复连预置 `CONNECTING`。
3. `settleOneKukuChannelAfterReady` **未** `setPhase(IDLE)`，相位残留。

## 修复

- 桥已就绪 → 卡片强制 READY，忽略 CONNECTING/STARTING 文案。
- settle / LaunchedEffect 清 CONNECTING→IDLE。
- 复连不再预置 CONNECTING（仅 prepare 真走 ADB 时才置）。

## 验证

- 编译 + 装包；`am force-stop` → 启动后 Hero 应稳定就绪（人工目视 + 相位不应长期 CONNECTING）
