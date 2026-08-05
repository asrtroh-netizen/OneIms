# OneRoot 发布到 OneSo-assets

## 变更
- 在公开仓 `asrtroh-netizen/OneSo-assets` 增加目录 `oneroot/`（PC 单窗一键临时 Root）
- 顶层 README 补充 OneRoot 启动说明
- 远端 tip：`bea91dc`

## 使用
```powershell
git clone https://github.com/asrtroh-netizen/OneSo-assets.git
cd OneSo-assets\oneroot
.\OneRoot.ps1
```

## 边界
- 不向公开 `OneIms` 仓推源码
- OneRoot 仍从本仓 catalog/so 拉 preload；不做运营商持久化
