# 变更说明 · 底栏「赞助」分页

日期：2026-07-13  
范围：导航 / 设置 / 赞助页 / assets

## 做了什么

1. **删除**：设置页「赞赏支持」「捐赠支持」两处入口。
2. **新建**：底栏独立目的地 `AppDestination.SPONSOR`（文案「赞助」），位于排障与设置之间。
3. **填充**：`assets/sponsor_wechat.jpg`（图二微信赞赏码）、`assets/sponsor_alipay.png`（图三支付宝码）；赞助页微信在上、支付宝在下。
4. **交互**：赞助改为 Tab 页，去掉原 overlay 返回按钮；底栏 6 项启用 `alwaysShowLabel = false` 减轻拥挤。

## 验证

- 静态引用与资源文件存在性 → PASS
- 真机扫码 / 底栏切换 → NOT RUN
