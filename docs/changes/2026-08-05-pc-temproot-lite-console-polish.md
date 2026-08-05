# PC-TempRoot-Lite：控制台美化

## 变更
- 彩色横幅、阶段标题、`[ok]/[FAIL]/[dry-run]` 状态色
- 文字进度条 `progress [####----] N%`
- 成功/失败收尾更醒目；提示 UI 包路径
- 同步到 `PC-TempRoot-UI/console/` 回退脚本

## 验证
- `一键临时Root.cmd dry nopause` → EXIT=0，输出含进度与 comet/0705 匹配
