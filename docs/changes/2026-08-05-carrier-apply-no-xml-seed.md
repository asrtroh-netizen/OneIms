# 应用运营商失败：无 carrierconfig XML + 安全 seed

## 现象

首页「应用运营商与我的配置」在已有临时 Root 时仍失败。

## 根因（真机证据）

`/data/user_de/0/com.android.phone/files/` 只有 `imsprovisioningstatus_*.xml`，**没有** `carrierconfig-*.xml`。  
旧逻辑「只改已存在文件、绝不新建」→ `TempRootCarrierXmlPersist` 返回 `no_carrierconfig_xml`。  
干净 Pixel 常见：CarrierConfig 走内存默认值，从未落盘覆盖文件。

## 修复

1. 无 XML 时按 `SubscriptionManager` 当前 SIM seed 最小 `<bundle/>`（命名对齐教程：`carrierconfig-com.google.android.carrier-{ICCID}-{carrierId}.xml`），再跑原补丁。
2. 安装脚本允许 create（仍 `chown radio` / 0600 / SELinux）。
3. 文案：`temp_root_carrier_apply_no_xml` 明确无 XML/无法 seed 时的失败原因。

## 验证

- 静态：`TempRootCarrierXmlPersist.kt` seed 分支；MainActivity 消息分支  
- 真机需重装/热更 App 后再点「应用运营商与我的配置」；本轮未出 APK → 运行时闭环 NOT RUN
