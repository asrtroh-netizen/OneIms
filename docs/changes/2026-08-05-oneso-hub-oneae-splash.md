# OneSo Hub：OneAE 启动页 + 0805 桌面打包

## 做了什么

1. **重新生成 P9 @ CP2A.260705.006 全家桶**（今日 08/05 桌面动作；真机仍是 0705 build）  
   `pack-0705` → tokay / caiman / komodo / comet **4/4**
2. **OneAE 风格 Hub**（HTML + pywebview，对齐 Qpanel 启动页：hero / orbit / chip / 体检分桶）  
   - `python tools/oneso/oneso.py hub`  
   - `.\scripts\oneso-hub.ps1`  
   - `.\scripts\temp-root-pc.ps1 -Hub`
3. Hub 内可：开始体检 / 生成 0705 全家桶 / TempRoot 预览 / 确认后执行 / 打开旧 Tk 工厂

## 非目标

- 未声称存在 `CP2A.260805.*` 新 build（当前设备与 TEMP 源 so 均为 0705）。
- 未默认自动跑长 exploit（TempRoot 执行需确认）。
