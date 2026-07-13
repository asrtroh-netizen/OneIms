# 2.0.4：信号阈值与格子数独立

## 现象
功能页「5G 信号强度调整」与独家页「信号格显示样式」共用同一偏好：关阈值会把格子打回 AUTO，选四/五格又会强制打开阈值。

## 修复
1. `ConfigStore`：阈值布尔与格子枚举分 key 读写，互不派生、互不回写。
2. `composeIndependentSignalPreset`：系统写入时按两侧偏好组合——阈值管 soft RSRP，格子管 inflate。
3. 能力页 Apply core / 独家页 Apply style 各自只改自己的偏好，再组合写 CarrierConfig。
4. 版本 **2.0.4**（versionCode 13）。

## 验证
- 单测：`composeIndependent_*` + 既有 signal preset 用例
- `testDebugUnitTest` + `packageNamedDebugApk` → `OneIms-2.0.4.apk`
- 真机：分别只开阈值、只改格子、两边同开，确认互不冲掉 —— NOT RUN（待用户）
