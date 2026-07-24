# 2026-07-25 · One Index 签名 + 会员 Token + CDN 预设

实现用户三连：ECDSA 防篡改、Bearer 私有索引、生态预设改挂 `cdn.oneims.app`。

验证：`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug` SUCCESS（含签名篡改拒绝单测）。
