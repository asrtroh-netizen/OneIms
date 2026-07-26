# OneTools 0.5.2 · 展开弹簧曲线（MT 干净室）

## 变更
- 新增 `CapsuleMotion`：展开 300ms / 收起 260ms + PathInterpolator 贝塞尔控制点
- 展开卡：alpha + 位移 + 缩放，套 expand/collapse 曲线
- 扁胶囊：展开时 1.08 过冲回弹
- 单测：`CapsuleMotionTest` 端点与单调性

## 边界
不引用 `com.pryshedko.*`；曲线为自有常量对照学习结果。
