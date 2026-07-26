# OneTools 0.4.2 · 扁胶囊：长度/高低分离 + 避摄默认位

对照 One Capsule「轻提醒态」概念图：黑色扁长胶囊，文案如「配送中 · 18分钟」。

## 变更
- 「大小」拆成 **长度** / **高低** 两个滑条（不再用单一 scale）
- 视觉改为扁长黑胶囊（小字号、紧上下 padding）
- 默认 Y 落在状态栏底边下方（+6dp），减少压住前置摄像头；可用「上下」滑条贴岛
- 预览文案改为「配送中 · 18分钟」
- 版本 → `0.4.2` / `24`（本轮按用户要求不打包 APK）

## 路径
- `LiveStatusCapsuleOverlay.kt` / `LiveStatusPrefs.kt` / `LiveLabScreen.kt` / `strings.xml`
