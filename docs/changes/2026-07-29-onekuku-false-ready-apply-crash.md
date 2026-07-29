# OneKuku 假就绪 + Apply 闪退

**日期**：2026-07-29  
**现象**：一加独立版 snackbar 显示「就绪」但用户称未激活；点应用闪退。Lite 侧 OEM key=26/27 软提示属预期。

## 根因

1. Success 路径只刷 `shizukuRunning`，`shizukuGranted` 可能 stale → Hero 假 READY  
2. `settleOneKukuChannelAfterReady` 无 `isReady()` 门禁  
3. 「通道已拉起，请确认授权」在已就绪时也会弹，文案误导  
4. Apply 缺 `ensurePrivilegedAccess`；信号条失败会 throw

## 修复

- `syncPrivilegeUiAndPublishActivation()` 双字段同步 + 文案分流  
- settle / Apply 硬门禁；信号条失败改返回字符串

## 验证

- 双 flavor 编译 PASS  
- 一加真机：NOT RUN（需装本修复后的包）
