# OneKuku 核心组件（可选内置）

将兼容 binder 协议的核心服务 APK 命名为 `onekuku-core.apk`，放到本目录（与本 README 同级的上一级 `assets/`）：

```
app/src/main/assets/onekuku-core.apk
```

打包后，「准备 OneKuku 核心」会优先安装该内置包，**不再跳转应用商店**。

若未放置该文件，运行时会从官方 Release 源下载核心组件（产品文案仍为 OneKuku）。

许可与归属以该 APK 上游许可证为准；分发前请自行核对。
