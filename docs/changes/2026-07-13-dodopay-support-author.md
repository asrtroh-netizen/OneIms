# 2.0.6：DodoPay 支持作者页

## 变更
- 去掉支付宝 / 微信静态赞赏码与 assets
- 底栏「支持作者」+ 设置/关于入口进入 DodoPay 流程
- 客户端只构造公开支付链接并 `ACTION_VIEW` 打开；不持有 API Key
- Deep link `oneims://support/callback` 提取 `payment_proof`，走公开验证接口
- 本地仅保存支持者标识与打码 proof；不影响 IMS/APN/5G/切卡

## 配置
在 `DodoPaySupportConfig` 填入：
- `SUPPORT_URL_TEMPLATE`
- `PROOF_VERIFY_BASE_URL`
- 可选 `SUPPORT_FEED_URL`

未配置时页面显示「暂未配置」，不崩溃。

## 验证
- `DodoPaySupportClientTest`
- `testDebugUnitTest` + `packageNamedDebugApk` → `OneIms-2.0.6.apk`
- 真机支付闭环：待填入真实链接后回归
