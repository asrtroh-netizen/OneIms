# 变更说明：原生内嵌 ADB 拉起 OneKuku 核心

**日期**：2026-07-14  
**依赖**：`libadb-android:3.1.1` + `conscrypt-android` + `sun-security-android`（Apache/双许可，Java 侧兼容 Kotlin 2.0.21）

## 做了什么

1. `OneKukuAdbMdns`：发现本机 `_adb-tls-pairing/_adb-tls-connect` 端口  
2. `OneKukuEmbeddedAdbActivator`：持久化 RSA 身份 → pair → connect → shell 执行 `start.sh`  
3. 首页/排障「准备核心」：已装核心时优先走内嵌 ADB；需要配对码时弹窗；失败回落剪贴板指引  
4. 权限：`CHANGE_WIFI_MULTICAST_STATE` / `ACCESS_WIFI_STATE`

## 刻意未做

- 升级项目 Kotlin 以强上 Kadb 2.1.1（元数据 2.3 不兼容）  
- 真机无线调试联调（本环境无设备）

## 验证

- `compileDebugKotlin` PASS（`--offline`）  
- 单测受 bcprov 首次下载/离线限制时可能 NOT RUN；核心契约测仍可用在线跑  
- 真机配对/启动 NOT RUN
