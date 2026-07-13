# 变更说明 · VoWiFi 名称格式改为弹出选择

日期：2026-07-13  
范围：能力页 UI（`CapabilitiesScreen`）+ 中英文字符串

## 做了什么

1. **交互**：能力页不再内嵌 13 项 VoWiFi 格式单选长列表；改为「当前格式」入口行，点击弹出 Dialog 选择。
2. **反馈**：入口副标题回显当前格式；选中后立即关闭弹层并更新预览（应用仍走原「应用 VoWiFi 名称」按钮）。
3. **复用**：弹层单选项复用 `SettingsChoiceRow`；Dialog 壳对齐 `ApnCatalogDialog` 的全宽居中 Surface。

## 验证

- 静态代码与字符串资源对照 → PASS
- 真机点选/关闭/跟随系统→自定义名启用 → NOT RUN
