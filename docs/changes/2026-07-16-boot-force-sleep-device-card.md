# 开机强制自配休眠 + 首页设备详情框

## 需求

1. 适当加强开机：通道就绪后务必自动配置一次，然后休眠（双产品）
2. OneKuku 首页太空：设备详情做成独立框（消息「放在」处截断，默认放在通道卡下方）

## 改动

### 开机 `OneKukuBootRestoreCoordinator`

- 收尾由 `sleepIfEnabled` 改为 `sleepAfterBootConfig` → **强制** `OneKukuSleepController.sleep`（不看「用完自动休眠」开关）
- OneLink：Wi‑Fi 已起但 Shizuku 未就绪时，再 `awaitBridgeReady(25s)` 等 binder+授权，提高「自动配置一次」命中率

### 首页 `HomeScreen`

- 通道卡下新增 `DeviceDetailsCard`（`SectionBlock` 框，默认 4 行预览，可展开/收起）
- 去掉 StatusHero 角落「设备详情」芯片与弹窗路径

## 验证

- 编译命令见交付总结
- 真机冷开机 / 首页布局：人工
