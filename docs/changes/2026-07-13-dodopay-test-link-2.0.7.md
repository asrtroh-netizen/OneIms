# 2.0.7：写入 Dodo Test Payment Link

## 变更
- `SUPPORT_URL_TEMPLATE` 填入 Test Mode 结账链接  
  `https://test.checkout.dodopayments.com/buy/pdt_0Nj5PdEVXI4wD8vxMrKQ0?quantity=1`
- 官方 `*.dodopayments.com` 链接原样打开，不再追加 proof query（避免干扰结账）
- `PROOF_VERIFY_BASE_URL` 仍为空：支付后**不会**自动 proof 解锁（需后续接 Webhook/公开校验或 Live 方案）

## 验证
- `testDebugUnitTest`
- `packageNamedDebugApk` → `OneIms-2.0.7.apk`
- 真机：支持作者页应能打开 Dodo 测试结账；用测试卡 `4242…`
