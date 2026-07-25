# 2026-07-25 · Caller：系统拨号原生归属地

## 用户意图澄清

要比的不是设置页 UI，而是 **Pixel 系统拨号 / 来电界面里原生那一行归属地**（Contacts Directory）。

**硬边界（用户明确）**：**不要看来电悬浮**——悬浮窗不算原生归属地，也不做为验收。

## 实现

- `ContactsContract.Directory` + `phone_lookup`（已有）现在会对：
  - **号段归属地**（`assets/caller/geo_v1.json` · schema `onetools.geo.v1`）
  - **自定义标签 / 拦截**（LABEL / ALLOW / BLOCK）
  合成一行精致文案，例如 `工商银行客服 · 北京 · 移动`
- `DialerLabelComposer` 统一排版
- 试查预览显示「拨号器将显示：…」

## 许可

**禁止**捆绑 GPL 的 `phone.dat`（xluohome/phonedata）。起步库为干净室自建 JSON；完整号段库后续走 OneBlock CDN（自有数据）。

## 验收（Pixel）

1. 设为默认「来电显示与骚扰拦截」
2. 授予通话记录权限
3. 用未存通讯录的手机号打进 / 在拨号盘输入 → Phone 应显示归属地行
