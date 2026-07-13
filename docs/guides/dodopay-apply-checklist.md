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

材料建议提前备好：产品说明、身份证件、（企业）营业执照、与身份一致的银行账户、可访问的产品页或 GitHub Release 页。

---

## 个人（Individual）怎么注册、怎么填

### 别走错入口

| 页面 | 个人能用吗 |
|---|---|
| [初创计划表单](https://dodopayments.com/zh/startups) | ❌ 常要求 **工作邮箱**，个人邮箱容易卡死 |
| [Dashboard 登录/注册](https://app.dodopayments.com/) | ✅ **个人用这个**：邮箱收 6 位 OTP 登录（无密码） |

官方说明：无注册公司也可选 **Individual**；个人收款绑到你本人。

### 验证顺序（Individual）

1. Dashboard → Verification  
2. 账户类型选 **Individual**（不要选 Registered Entity）  
3. Product Information Form  
4. Identity Verification（政府证件 + 自拍）  
5. Bank Verification（户名必须与证件姓名一致）  
6. 等待审核（常见 1–3 个工作日）

个人 **跳过** Business Verification（KYB）。

### ⚠ 重要：纯「打赏/捐赠」会被拒

Dodo Payments [Merchant Acceptance Policy](https://docs.dodopayments.com/miscellaneous/merchant-acceptance) 明确不接受：

- Donations（收钱却不交付对价、仅一般支持平台）
- Fundraising / 众筹 / 个人募捐等无明确产品服务的贡献模式

因此 **不要** 在产品描述里写「donation / tip / 打赏 / 自愿支持无回报」。  
若坚持用 Dodo，需改成**有明确交付物**的数字商品，例如：

- 支持者感谢页 + 应用内支持者标识（已有本地 unlock）
- 额外交付：支持者专属说明 PDF、贴纸包、更新日志邮件、私有 Discord/群邀请等之一

拿不准先发信问：`compliance@dodopayments.com`。

纯打赏更适合：爱发电 / Ko-fi / 国内收款码等（与本仓 proof 契约另议）。

### Product Information 可照抄（有对价版，按实情微调）

| 字段 | 个人怎么写 |
|---|---|
| Product website | `https://github.com/asrtroh-netizen/OneIms` 或 Release 页 |
| Product description | `OneIMS Supporter Pack: digital thank-you + in-app supporter badge for the OneIMS Android utility (IMS/VoLTE tooling). Buyer receives acknowledgment after payment.` |
| Product category | Software / digital content（以仪表盘选项为准） |
| Delivery method | Instant digital acknowledgment / in-app badge unlock |
| Automation | Partially automated |
| Integration method | Payment Links / Checkout |
| Social | GitHub 仓库链接 |

**禁止照抄**：`voluntary donation with nothing in return` / `just tip the author`。


### 身份与银行

- 护照 / 身份证 / 驾照等政府证件 + Persona 自拍  
- 银行卡户名 = KYC 姓名；**不要**填父母/他人卡（除非整户都以对方名义注册）  
- 相机权限要开，否则 KYC 会报 Can't access camera  

### 常见卡点

1. 卡在 Startups「Work email」→ 改走 `app.dodopayments.com`  
2. 误选 Registered Entity → 在表单仍审核中可改回 Individual；已批准后需找客服  
3. 「捐赠/打赏」表述可能碰政策红线 → 写成 **digital product / creator support for software**，并先读 [Merchant Acceptance Policy](https://docs.dodopayments.com/)  
4. 仍失败 → `support@dodopayments.com` 或 Dashboard Get Support，附截图  

---

## 建议动作顺序

1. 个人：打开 https://app.dodopayments.com/ 用常用邮箱收 OTP  
2. Verification → Individual → 按上表填产品信息 → KYC → 本人银行卡  
3. 通过后建 Payment Link；若要喂进 OneIMS proof 契约，再开一轮对接（勿把 API Key 写进 APK）
