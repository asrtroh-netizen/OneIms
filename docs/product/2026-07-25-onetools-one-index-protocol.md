# One Index · 签名 / 会员 Token / CDN（可售卖）

## 已落地

1. **防篡改签名**：ECDSA P-256 + `SHA256withECDSA`  
   - 字段：`keyId` / `sigAlg` / `signature`  
   - 公钥：`assets/one-index-keys.json`  
   - 客户端：`OneIndexVerifier`（`ONE_INDEX_REQUIRE_SIGNATURE=true`）  
   - 签发脚本：`onetools/scripts/sign_one_index.py`（私钥 gitignore）

2. **会员 Token 私有索引**：  
   - 索引 `"auth": "bearer"` 时必须带 Token  
   - UI：更新页「设置会员 Token」→ DataStore  
   - 请求头：`Authorization: Bearer <token>`

3. **生态预设改挂自有 CDN**：  
   - `BuildConfig.ONE_CDN_INDEX_URL` 默认 `https://cdn.oneims.app/onetools/one-update.json`  
   - presets 全部 `AppSource.ONE_INDEX`  
   - 样例索引：`assets/sample-one-update.json`（已签名，上线前替换真实 APK URL 并重新 sign）

## 密钥轮换

```bash
python onetools/scripts/sign_one_index.py rotate-keys
python onetools/scripts/sign_one_index.py sign path/to/one-update.json
```

- 新 `keyId`（当前：`one-cdn-2026r2`）写入 `assets/one-index-keys.json`
- **保留旧公钥**一段时间，便于 CDN 与 App 版本交叉过渡
- 私钥仅本地：`onetools/scripts/one-index-dev-private.pem`（gitignore）

## CDN 上线清单

1. 上传 `one-update.json`（先 `python sign_one_index.py sign path`）  
2. 上传各 APK 到 `cdn.oneims.app/releases/...`  
3. 私有源：CDN 校验 Bearer，索引设 `"auth":"bearer"`  
4. 轮换密钥：新 keyId 写入 `one-index-keys.json` 并发版 App

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
