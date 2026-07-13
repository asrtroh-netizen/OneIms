# OneIMS「支持作者」开通清单（DoDoPay）

> 对应客户端：`DodoPaySupportConfig`（只放公开链接，绝不放 API Key）

## 关键：两套完全不同的「Dodo」

| | **A. DoDoPay（CarrierIMS 同款）** | **B. Dodo Payments（海外 MoR）** |
|---|---|---|
| 站点 | https://pay.dodododo.org/ | https://app.dodopayments.com/ |
| 本质 | 个人微信/支付宝收款网关 + 公开支持页 + `payment_proof` | 全球 SaaS 记录商户 |
| CarrierIMS | ✅ `https://pay.dodododo.org/support/app_…` | ❌ |
| 结账页支付宝/微信 | ✅ 个人收款码（`channel=ALIPAY\|WECHAT`） | 跨境钱包/卡，不是国内个人码那套 |
| 校验 | `GET {origin}/api/public/payment-proofs/{proof_…}` | Webhook / API Key（禁入 APK） |
| 留言墙 | `/api/public/support-feeds/{feed_id}` | 无此契约 |

证据：[ryfineZ/carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel) 的 `gradle.properties`：

```properties
turboims.dodopaySupportUrlTemplate=https://pay.dodododo.org/support/app_c5b4614bad018dbd
turboims.dodopaySupportFeedUrl=https://pay.dodododo.org/api/public/support-feeds/public_feed_1ecc740076ba24d8c6eb15cc
```

规格摘要见该仓 `docs/superpowers/specs/2026-06-16-ui-support-commerce-redesign.md`：  
App 内选支付宝/微信 → 打开 DoDoPay 公开页并带 `auto_checkout=1` → 页上展示个人码 → 回跳 `checkout/close#payment_proof=` → App 调公开校验接口。

**想要「别人 CarrierIMS 里那种捐赠」→ 走 A，不要继续死磕 B。**

---

## 路线 A：申请 DoDoPay（推荐）

1. 打开 https://pay.dodododo.org/ → **进入管理后台** / 看 **接入文档** https://pay.dodododo.org/docs  
2. 创建应用，拿到：  
   - `https://pay.dodododo.org/support/app_xxxxxxxx`  
   - 可选 Feed：`https://pay.dodododo.org/api/public/support-feeds/…`  
3. 后台绑定微信赞赏码、支付宝收款码  
4. 填入 OneIMS：  
   - `SUPPORT_URL_TEMPLATE` = 支持页  
   - `PROOF_VERIFY_BASE_URL` = `https://pay.dodododo.org`  
   - `SUPPORT_FEED_URL` = Feed（可选）  
   - `APP_ID` 与路径 `app_…` 一致  
5. 客户端恢复/保持 CarrierIMS 契约：追加公开 query、拦截 close 页、校验 `proof_`（当前 2.0.7 测试链是 B，正式应对齐 A）

---

## 路线 B：海外 Dodo Payments（你已注册的）

- 适合国际卡；Test Link 可测「打开浏览器」  
- **没有** CarrierIMS 的 `payment_proof` 公开接口 → 自动解锁要另接 Webhook  
- 个人注册：用 Dashboard OTP，不要用 Startups 工作邮箱表单  
- Test Mode：Dashboard 开 Test → 一次性产品 → 分享 Payment Link；测试卡 `4242424242424242` / `06/32` / `123`

---

## 建议

1. 若目标是国内支付宝/微信码 → 立刻去 **pay.dodododo.org** 开应用  
2. 拿到 `support/app_…` 链接后交给开发写入配置并改回官方结账原样打开以外的 proof 参数逻辑  
3. 海外 Dodo Payments 账号可保留作备用，不必强行当 CarrierIMS 捐赠用  
