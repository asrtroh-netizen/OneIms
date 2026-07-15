# 2026-07-15 · OneKuku 激活方案收敛（2.0.20）

## 目标
- 移除「大体积完整 adb / 终端 / 复制命令 / App 内默认六位码 / Shizuku」心智
- Mini ADB Client：只 pair / connect / 白名单 shell
- 默认通知栏 RemoteInput 填码（无需切回 App）

## 实现
- `OneKukuMiniAdbClient`：输入解析 + 委托现有 TLS 轻量库（非完整 adb binary）
- `OneKukuPairingNotification`：规格文案（等待/配对中/成功/失败）
- `prepareOneKukuCore`：默认不弹 App 内输入框；有 transport 直接启动
- 首页激活区去掉热点/复制命令入口；一键恢复无通道时先走通知配对
- 版本 `2.0.20` / code `29`

## 验证
- 单元测试：`OneKukuMiniAdbClientTest`、`OneKukuCoreComponentTest`
- 真机通知填码：NOT RUN（待装包）
