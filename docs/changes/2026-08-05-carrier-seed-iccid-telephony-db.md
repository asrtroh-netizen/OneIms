# CarrierConfig seed：ICCID 从 telephony.db 回退

## 现象

`3.2.9` seed 逻辑依赖 `SubscriptionManager.activeSubscriptionInfoList[].iccId`，
真机 logcat：

```
W OneIMS-TempRootCcXml: seed skip subId=5: empty iccid
W OneIMS-TempRootCcXml: seed skip subId=4: empty iccid
```

→ `no_carrierconfig_xml`，「应用运营商」失败。同一次流程里「我的配置」重放仍可 `ok=true`。

## 根因

Android 对普通 App 常清空 / 脱敏 `SubscriptionInfo.iccId`；
`/data/user_de/0/com.android.providers.telephony/databases/telephony.db`
的 `siminfo` 表仍有真实 `icc_id` / `carrier_id`（需 Root 可读）。

## 修复

`TempRootCarrierXmlPersist.seedMissingCarrierConfigFiles`：

1. 仍优先用 `SubscriptionManager` 的 iccid / carrierId
2. 为空时 `su` 拷贝 `telephony.db` 到 App cache，用 `SQLiteDatabase` 读 `siminfo`
3. 按 active `subId` 对齐后 seed 最小 `<bundle/>` 再 patch

版本：`3.2.10` / `versionCode 92`（ICCID 回退）；`3.2.11` / `93`（幂等已 ok 不算失败）。

## 续：参考失败 + 我的成功

真机 log（3.2.10，XML 已含最小键）：

```
already/skip unchanged …xml
xml_patch_none
reapply attempted=true ok=true
```

根因：`ok = patched > 0` 把「无需再写」当成失败。  
修复：全部 already ok 且无写失败 → `xml_already_ok=N` 且 `success=true`。
