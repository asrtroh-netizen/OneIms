# 2.0.8：恢复微信赞助码页

## 变更
- 支持作者页改回本地二维码展示
- **仅保留微信赞助码**（`sponsor_wechat.jpg`）
- 去掉支付宝入口与支付宝资源
- 清空 DodoPay 测试结账链接；Dodo 客户端代码暂留但不驱动 UI

## 验证
- `testDebugUnitTest` + `packageNamedDebugApk` → `OneIms-2.0.8.apk`
