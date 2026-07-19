# 双产品 Root 开机开关

**日期**：2026-07-19  
**产品**：**OneIMS（OneKuku）** + **OneIMS Lite（OneLink）**（同仓双 flavor）

## 需求

两个产品各自 Root 开关；打开后开机拉起**对应特权桥**（不是改外置 Shizuku 仓当第二产品）。

## 落地

### 共用

- `ConfigStore.root_boot_start`（默认关）
- `BootReceiver` BOOT_COMPLETED → `RootBootStarter.maybeStartOnBoot`（失败不影响原重放）
- 实验功能页：「Root 开机拉起通道」

### OneIMS / OneKuku

- `su -c` + `OneKukuCoreComponent.bridgeBootShellCommand` → `onebridge_server`

### OneIMS Lite / OneLink

- `su -c` + `ShizukuSetupHelper.buildShizukuRootStartCommand`（`libshizuku.so --apk=…`）
- 回答「多数 Shizuku Root 后仍要手点」：Lite 开此开关即代拉，无需再进 Shizuku 手点（仍需已安装 Shizuku + Magisk 授权 su）

## 验证

- `compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin`
- 真机 Root 冷启：NOT RUN
