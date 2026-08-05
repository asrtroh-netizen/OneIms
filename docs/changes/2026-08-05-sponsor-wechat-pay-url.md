# 赞助页：可选微信赞赏直达链

## 现状

`assets/sponsor_wechat.jpg` 为个人微信赞赏码。本机多种解码器（zxing-cpp / OpenCV）均无法读出载荷——个人赞赏码常为微信专用编码，**不一定存在**对外短链。

## App 行为

| `sponsor_wechat_pay_url` | 「打开微信」 |
|---|---|
| 空（默认） | 打开微信 + 赞助码存相册（扫一扫→相册） |
| 非空 | 优先 `ACTION_VIEW` 该 URL（建议 `wxp://…` 或可被微信打开的 `https://…`） |

配置位置：`app/src/main/res/values/strings.xml` → `sponsor_wechat_pay_url`。

## 若以后拿到链怎么填

把整段链接贴进：

```xml
<string name="sponsor_wechat_pay_url" translatable="false">wxp://f2f0xxxxxxxxxxxx</string>
```

重装即可；不必改 Kotlin。

## 怎么自己挖链（不一定挖得到）

1. 用另一台已登录微信的手机「扫一扫」本码，看付款页是否可分享（多数个人赞赏**不能**分享出链接）。
2. 用能显示「原始文本」的扫码 App；若仍无结果，基本可认定无标准短链。
3. 若改用微信支付「收款码 / 商家码」且后台提供链接，再填入上述字符串。
