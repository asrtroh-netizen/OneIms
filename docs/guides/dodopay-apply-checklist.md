# OneIMS「支持作者」开通清单（DodoPay）

> 对应客户端配置：`DodoPaySupportConfig`（只放公开链接，绝不放 API Key）

## 先搞清：你要申请的是哪一种？

| 类型 | 是什么 | 能否直接喂给当前 OneIMS 客户端 |
|---|---|---|
| **A. 公开结账 + payment_proof**（本仓已实现的契约） | 支付页 URL + `{BASE}/api/public/payment-proofs/{proof}` | ✅ 直接填三条 URL 即可 |
| **B. 商业版 Dodo Payments**（[dodopayments.com](https://dodopayments.com/)） | MoR / Dashboard / API Key / Webhook / 产品 ID | ⚠️ **不能**把 API Key 塞进 APK；需另做「公开结账页 + proof 校验服务」对接，或改客户端契约 |

当前 App 走的是 **A**。若你手里只有 B 的商户账号，需要额外一层公开服务，或改集成方式。

---

## 路线 A：已有 / 将有 proof 公开服务时

准备好后填入：

1. `SUPPORT_URL_TEMPLATE` — 公开支付页基址（客户端会追加 amount / payer_name / client_ref / proof_key / return_url 等）
2. `PROOF_VERIFY_BASE_URL` — 校验基址（请求 `…/api/public/payment-proofs/{payment_proof}`）
3. 可选 `SUPPORT_FEED_URL` — 留言墙 JSON
4. 确认 `APP_ID=oneims`、`PROOF_KEY=support_unlock`、`CALLBACK_URI=oneims://support/callback` 与服务端一致

回跳：支付页在成功后把 `payment_proof=proof_…` 带到 `oneims://support/callback`。

---

## 路线 B：去 Dodo Payments 官网申请商户（参考）

1. 打开官网注册 / Dashboard：https://app.dodopayments.com/
2. （可选）初创计划：https://dodopayments.com/zh/startups  
   - 约 2 分钟表单；审核约 3 个工作日  
   - **工作邮箱**（个人邮箱可能被拒）
3. 完成 Account Verification（产品信息表 → KYC → 企业则 KYB → 银行收款信息）  
   文档：https://docs.dodopayments.com/miscellaneous/verification-process
4. Dashboard 创建 One-Time 产品（如「Support OneIMS」）、记下产品 / Payment Link
5. **不要把 API Key / Webhook Secret 写进 App**  
   - 若要坚持当前 proof 契约：自建或复用一层公开结账 + `/api/public/payment-proofs`  
   - 若改用官方 Payment Link + Webhook：需另开一轮改造客户端

材料建议提前备好：产品说明（自愿支持 / 捐赠性质说明）、身份证件、（企业）营业执照、与身份一致的银行账户、可访问的产品页或 GitHub Release 页。

---

## 建议动作顺序

1. 先确认你申请的是 A 还是 B（有截图/邀请链接最好发来）
2. B：先注册 + 验证；A：直接拿到三条公开 URL
3. 把 URL 交给开发填入 `DodoPaySupportConfig` → 打一版 APK → 真机小额验证
