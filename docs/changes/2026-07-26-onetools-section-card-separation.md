# 变更说明 · OneTools 区块容器视觉分割

## 现象

特色功能等页不同区块黏在一起，缺少 OneIMS 那种「标题 + 圆角容器」的块感。

## 改动

- `OneToolsToolPage` 默认块间距 12 → **20**（对齐 OneImsPage）
- `OneToolsSettingsGroup` / `OneToolsInfoCard`：tonalElevation + 细描边
- 新增 `OneToolsInlineNotice`（状态提示独立容器）
- 特色页：通道状态 / 选卡 / 结果反馈各自进容器；三大功能块间距拉开
- 版本 → `0.3.9` / `21`
