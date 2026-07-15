# 2026-07-15 · 通知栏填六位码后仍「等待配对」（2.0.22）

## 现象（V2.0.21 真机）
- USB ADB 在线；下拉通知可输六位码
- 输完后通知/UI 仍停在「OneKuku 等待配对」，体感「没奏效」

## 日志证据
- 系统多次 `AdbPairingThread: Pairing succeeded … OneKuku`（pair 本身成功）
- USB 直拉 `BridgeService` → `binder sent to com.oneims.app.onebridge` PASS
- 失败路径被 `showFailure()` 立刻再 `showWaiting()` 盖掉，用户看不到真实 reason

## 根因
1. **UX 遮羞**：失败通知瞬间被等待通知覆盖
2. **pair 后端口快照过期**：未重扫 `_adb-tls-connect`
3. **shell 流关闭 / 缺 nohup**：`onebridge_server` 可能被会话带走，binder DeathRecipient 清空 → `binder_not_received`
4. `pairPortOverride` 只记日志未真正用于 pair

## 修复（2.0.22 / code 31）
- 失败通知保留可读 reason + RemoteInput 重试
- pair 成功后重扫 mDNS；启用端口 override
- `bridgeBootShellCommand` 使用 `nohup`；shell 流内等待 binder（12s）
- 失败 reason 中英文案

## 验证
- 单测：`OneKukuCoreComponentTest`（含 nohup）
- 真机：装 2.0.22 → 开无线调试配对页 → 通知填码 → 应成功或显示明确失败原因（不再静默回等待）
