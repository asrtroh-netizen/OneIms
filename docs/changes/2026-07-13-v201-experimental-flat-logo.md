# V2.0.1：国家码进独家 + 扁平 Logo

## 变更
1. **SIM 国家码覆盖**从能力页「修复工具」迁至「独家功能」（含预设芯片、应用/清除、TikTok US）。
2. **版本**：`versionName=2.0.1`，`versionCode=10`（高于 2.1.0 的 9，便于覆盖安装）；产物命名约定改为 `OneIms-<version>.apk`（**不再**附加 androidXX）。
3. **Logo**：恢复扁平红底 `#D6242F` + 纯白 O 环（去掉毛绒渐变/阴影）。

## 验证
- `:app:compileDebugKotlin` + `packageNamedDebugApk`
