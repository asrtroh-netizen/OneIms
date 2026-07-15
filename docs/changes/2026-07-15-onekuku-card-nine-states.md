# 2026-07-15 · 首页 OneKuku 状态卡 9 态（2.0.24）

## 背景
规格「OneKuku 状态」9 态：未激活 / 等待配对 / 配对中 / 连接中 / 启动中 / 已激活 / 休眠中 / 执行中 / 失败。
旧实现仅 4 态（INACTIVE/SLEEPING/RUNNING/COMPLETE），激活过程靠 detailOverride 凑文案。

## 改动
- `OneKukuCardState` 扩为 9 枚举；`OneKukuCardPolicy.fromActivationPhase` + `resolve`
- `StatusHero` 按态切换标题/胶囊/底色（警示红 / 进行中主色 / 就绪白）
- 进度条仍 4 段，9 态映射到点亮数
- 中英文案补齐；单测覆盖映射

## 顺带
2.0.23 真机日志已确认：`__OB_BOOT_OK__` + `OneBridge binder received` 成功。
