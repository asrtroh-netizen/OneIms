# 2026-07-25 · Caller 锁定最省钱离线路线

## 拍板

用户：目前走最省钱 → **不采购**商业查号 API。

## 代码

- `CallerPrefs.noNetworkQuery` 默认改为 **true**
- `ONE_CALLER_QUERY_URL` 保持空（既有）

## 文档

- `docs/product/2026-07-25-onetools-pixel-exclusive.md` 增加成本硬边界
- 报价调研文标注「已选不买」

## 验收主路径（零采购）

1. MIT `geo.dat` → Directory 通话记录省市  
2. OneBlock 双写 / onespam zip → 本地骚扰  
3. CallScreening 仅提示默认仍可开  
