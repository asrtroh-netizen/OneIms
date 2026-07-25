# 2026-07-25 · OneTools 反馈二轮（0.2.2）

## 变更

1. **录音悬浮**：点击改后台线程（修 ANR）；圆形麦克风 FAB + 忙碌环 + 录音脉冲  
2. **拦截清除**：移除 `CallScreeningService`；引擎永不拒接；举报写 LABEL；录音并入「通话」页  
3. **网速**：去掉应用流量统计；QS 磁贴动态图标+副标题；通知点击打开网速页；开关无权限不再假勾选  
4. **首页**：去掉状态卡下文案；快捷开关（网速/悬浮/电池）对齐 OneIMS 首页节奏  

## 验证

- `:onetools:testDebugUnitTest` PASS  
- `:onetools:assembleDebug` PASS  
- 真机 ANR / 磁贴 / 通知点击 **待复测**
