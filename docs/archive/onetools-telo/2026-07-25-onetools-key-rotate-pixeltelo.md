# 2026-07-25 · One Index 密钥轮换 + Pixel Telo 外置集成

## 密钥轮换

- 脚本支持 `rotate-keys`：新增 `one-cdn-2026r2`，保留 `one-cdn-2026`
- 样例索引已用新密钥重签
- 验签单测应继续通过（含新旧公钥映射）

## Pixel Telo

- Apache-2.0；包名 `vip.mystery0.pixel.telo`
- 首页卡 + `TeloScreen`：安装（GitHub Release）/ 打开 / 引导默认应用设置
- 更新中心 presets 增加 GitHub 源；不合并 CallScreening 源码

验证：`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
