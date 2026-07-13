# 变更说明 · Dock 常显文字 + 赞助页扫码跳转

日期：2026-07-13  
范围：`OneImsComponents` 底部导航、`SponsorScreen`、Manifest `queries`、中英文案

## 做了什么

1. Dock / `NavigationBar`：改为悬浮胶囊岛（左右/底部留白、大圆角、阴影）；`alwaysShowLabel = true`；选中态 primary 胶囊高亮 + 着色图标/文字。
2. 赞助页：微信 / 支付宝各增加「打开…扫一扫」按钮；深链分别为 `weixin://scanqrcode` 与 `alipays://platformapi/startapp?saId=10000007`。
3. Manifest 增加包可见性 `queries`；未安装或唤起失败时 snackbar 降级提示。
4. 文案明确边界：只能打开扫一扫，不能把页内二维码自动塞进微信/支付宝。
5. 参考图中的「添加贴文」FAB 属对方 App 场景，OneIMS 未照抄。

## 边界

- 用户仍需对准页内二维码，或在对方 App 从相册选择截图。
- 部分厂商 ROM 可能拦截自定义 scheme；失败路径已覆盖。
