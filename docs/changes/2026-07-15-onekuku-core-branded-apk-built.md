# 2026-07-15 · 换皮 OneKuku Core Debug APK 已编出并内置

## 产物

| 项 | 值 |
|---|---|
| 包名 | `com.oneims.onekuku.core` |
| 显示名 | OneKuku Core |
| 路径 | `app/src/main/assets/onekuku-core.apk` |
| 构建源 | `e:\GQ\One\OneKukuCore`（Shizuku v13.6.0 fork） |
| 变体 | debug（本机签名） |

## 构建环境补丁（邻仓）

- 拉取匹配的 `api` 子模块 commit `510fc98`
- 去掉已不存在的 `:hidden-api-stub` include
- Gradle wrapper 改用本机 `gradle-8.11.1-all.zip`（避开 8.14 下载超时）
- NDK `27.0.12077973` / build-tools `35.0.0`（对齐本机 SDK）
- 使用 Microsoft JDK 21.0.7 编译（依赖含 class file 65）

## 验收

```text
aapt dump badging … | package: name='com.oneims.onekuku.core'
application-label:'OneKuku Core'
```

## 后续

- 真机安装后点「启动核心」应优先识别换皮包
- 需要正式分发时再打 release 签名包并上传自有 Release（`OneKuku-core*`）
