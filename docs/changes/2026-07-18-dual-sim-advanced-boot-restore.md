# 双卡开机恢复高级选项（2026-07-18）

## 根因

高级选项原先是全局单槽（`advanced_has` + `advanced_sub_id` + 一组 `advanced_*`）。
后一次「应用高级选项」会覆盖前一张卡的持久化；开机 `ReapplyManager` / 快照 / `restoreAdvanced` 都只认这一张归属卡。

## 修复

- `ConfigStore`：按 `subId` 存 `advanced_has_$subId` / `advanced_*_$subId`；旧全局键一次性迁移到原归属卡。
- `ReapplyManager`：对 `listAdvancedOptionSubIds()` 逐卡 `applyOptions`。
- 快照与 `OneKukuRestoreManager.restoreAdvanced`：按本卡 prefs，禁止串卡。

## 升级注意

升级前若只对一张卡点过「应用」，prefs 里仍只有一张——需对第二张卡再点一次「应用高级选项」后，冷启才会双卡都恢复。

## 验证

- `:app:compileOnekukuDebugKotlin` / `compileOnelinkDebugKotlin` PASS
- 真机双卡冷启：需用户回归（曾分别对两卡应用）
