# Shizuku 冷启伪成功修复（V15.0.0）

## 现象

装 V15 后冷启：`SelfStarter` 很快 `STOP_SERVICE`，无 `shizuku_server`；开 App 才起来。

## 根因

`startShizukuViaAdb` 在 shell 命令返回后立即 `onSuccess`，冷启时 binder/`shizuku_server` 尚未就绪 → 伪成功并停掉 FGS。

## 修复

1. 命令结束后轮询 `Shizuku.pingBinder()`（最长约 8s），未就绪则重试/失败回落  
2. 失败时清掉 stale last port，回落 mDNS + 端口轮询  
3. mDNS 前必要时打开无线调试  
4. 保留 direct SelfStarter 热路径（未提交改动一并入库）

## 验收（Pixel 9 Pro Fold）

| 步骤 | 结果 |
|---|---|
| install -r V15 修复包 | Success |
| 冷启 ≤15s `shizuku_server` | **PASS**（日志可见 binder-not-ready 后恢复） |
