# 变更说明 · 灵动岛胶囊显示

## 需求

用户要「胶囊 / 灵动岛胶囊」可见展示，不只依赖系统 Live Update 芯片。

## 落地

- `LiveStatusCapsuleOverlay`：顶栏居中黑底圆角胶囊
- Live Lab：胶囊开关 + 悬浮窗权限引导 +「预览胶囊」演示按钮
- Hub 发布/清除时同步胶囊
- 版本 → `0.4.0` / `22`

## 验证

```text
.\gradlew :onetools:compileDebugKotlin
powershell -File onetools/scripts/build-local-apk.ps1
```
