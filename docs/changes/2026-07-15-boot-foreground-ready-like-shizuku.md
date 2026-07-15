# 开机前台就绪：对齐 Shizuku「打开已是就绪」

## 动机

冷开后旧网可无码激活，但用户体感仍「进软件点半天才生效」。日志有 `Background start not allowed` 拦 `OneKukuBootRestoreService`，且通道激活排在等 SIM 之后。

## 改动

1. `OneKukuBootRestoreService` → **前台短生命周期**（`startForegroundService` + specialUse），避免开机后台 start 被拦  
2. Coordinator：**先静默激活通道，再等 SIM / 恢复配置**；通道就绪立即写 `READY_SLEEPING`  
3. BootReceiver：开机防抖 3s→1s，解锁 1s→0.5s  
4. 首页启动：已就绪立刻对齐 UI；已配对未就绪 `enqueue(0)` 立刻补跑  

## 验证

- 编译装包  
- 冷开：不应再出现 Background start not allowed；打开 App 应已是通道已激活/休眠  
