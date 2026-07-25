# 2026-07-25 · OneTools 特色功能 R1（方案 B 整栈移植首轮）

## 目标

将 OneIMS「独家功能」中的三项：

1. 信号格显示样式  
2. 5G 显示增强  
3. 控制中心快捷切卡  

**原生**迁入 OneTools「特色功能」，并从 OneIMS 独家页删除 UI。接受多轮补齐。

## 本轮已完成（R1）

### OneTools（`com.onetools.app`）

- 新建 `special/` 栈：
  - `SpecialPrivilege`：Shizuku 包装 activity / carrier_config / isub / phone  
  - `SpecialBroker` + `SpecialBrokerInstrumentation`：CarrierConfig override（root 直调 / 非 root Instrumentation 委托）  
  - `SignalBarController` / `FiveGDisplayController` + `SpecialFeatureStore`  
  - `DataSimController` + `DataSimTileService` + `TileHelper`  
  - `SpecialFeaturesScreen` UI  
- 设置页入口「特色功能」  
- Manifest：`instrumentation` + QS Tile  
- 依赖：`hiddenapibypass`

### OneIMS

- `ExperimentalScreen` 删除上述三项 UI 与无用 `FiveGThresholdField`  
- 独家页副标题改为指向 OneTools  
- **未删除** OneIMS 侧 Manager / 开机重放 / 旧 QS Tile 注册（R2 再收敛，避免旧偏好与 Tile 断链）

## 验证

```text
.\gradlew :onetools:compileDebugKotlin :app:compileOnelinkDebugKotlin
→ BUILD SUCCESSFUL
```

真机写入 / 磁贴切卡 / 重启后重放：**NOT RUN**（本轮仅编译门禁）。

## R2 建议

1. OneTools 开机重放（对齐 `ReapplyManager` 信号格 / 5G）  
2. 基线 ownership 与 OneIMS `SystemDisplayOverrideManager` 完全对齐  
3. 收敛 OneIMS 死代码与旧 QS Tile；会员 Pro「独家六条」文案产品对齐  
4. 从 OneIMS ConfigStore 迁移既有用户偏好到 OneTools（可选）
