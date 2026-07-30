# 2026-07-30 · OneBridge 脱离 ADB shell（防 SIGHUP）

## 现象（真机 c0b76e3b）

装包激活后 `state=ACTIVE`，约 2s 出现 `OneBridge binder died`，server PID 不断更换。

## 根因

`writeShellAndAwaitBinder` 在 libadb `shell:` 流内拉起 `app_process &`；流 `use{}` 结束关闭会话时 **SIGHUP** 带走未脱离会话的 `onebridge_server`。

## 修复

- `OneKukuCoreComponent.bridgeBootShellCommand`：`setsid … </dev/null &`，失败回落 `nohup`
- `bridge/assets/start.sh` 同步
- 前台多拍复连：进行中不重入；binder 就绪取消多拍；末拍不再 `forceRestart`

## 验证（本轮）

- `testOnekukuDebugUnitTest --tests OneKukuCoreComponentTest` PASS
- 装 `app-onekuku-debug.apk` 后 `pidof` 连续 12s+ 同 PID；`force-stop` 后 server 仍在
- log：`registerProcessObserver ok (pid-tracked)` + `state=ACTIVE` + UI「核心服务已激活」
