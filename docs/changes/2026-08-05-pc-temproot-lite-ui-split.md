# PC TempRoot：拆成简易版 / UI 版

## 变更
- 删除混淆目录 `pc-temp-root/`
- 新增：
  - `PC-TempRoot-Lite/`：纯 CMD 黑窗，无 UI
  - `PC-TempRoot-UI/`：进度监测窗 + OneIMS 推荐（GitHub / Releases）+ 微信赞赏码（`assets/sponsor_wechat.jpg`，与 App/oneso 同源）

## 验证
- Lite：`一键临时Root.cmd dry nopause` → EXIT=0（comet / CP2A.260705.006）
- UI：`-DryRun -AutoClose` → `DRY_OK …`；`assets/sponsor_wechat.jpg` 存在
