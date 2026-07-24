# 2026-07-25 · OneCaller vs Pixel Telo（自研补短板）

> 用户：Pixel Telo 也要做自己的；对比短板补上。  
> 策略：**干净室自研**（CallScreening / 规则引擎 / One 索引下发），不复制 Pixel Telo 源码；Apache 外置卡可保留作对照。

## 对照与补强

| 维度 | Pixel Telo | 短板 / 局限 | OneCaller（本轮）补法 |
|---|---|---|---|
| 产品形态 | 独立 App | 与 One 生态割裂 | **嵌在 OneTools**，与录音/更新/CDN 同壳 |
| 设备定位 | 主打 Pixel / 类原生 | OEM 话术弱 | 文档+探测不绑死 Pixel；后续可加 OEM 兼容表 |
| 拦截引擎 | CallScreeningService | 必须设默认应用（系统限制，双方都有） | 同 API；UI 强化「一键去设默认」 |
| 号码库 | 自有云端库 | 库归属对方 | **`onetools.blocklist.v1`** 走 One CDN + 可签名 |
| 名单 | 精确/前缀/标签 | 标签依赖其库体系 | MVP：**精确+前缀**；标签字段预留 |
| 拨号器标签 | Directory Provider | 实现重、API 37 权限敏感 | 本轮先 CallScreening；Directory **下一刀** |
| 隐私 | 本地优先 | 强项 | 规则默认本地 DataStore，不下发通讯录 |
| 与录音联动 | 无 | 缺口 | 拦截页入口跳转自研录音 |
| 可售卖 | 开源 Apache | 难做付费目录壁垒 | 会员 Token 拉私有拦截库（复用更新中心 Token） |

## 本轮 MVP 范围

- `OneCallScreeningService` 系统拦截
- 本地黑/白名单（精确 / 前缀）
- 从 URL / 粘贴导入 `onetools.blocklist.v1`
- 首页「来电拦截（自研）」主入口；Pixel Telo 降为可选对照
- **Directory Provider**（`phone_lookup` / `directories`）拨号器标签
- 拦截库发布：GitHub Release 镜像 + CDN 凭据到位后 PUT

## 明确非本轮 / 后续

- 完整 Contacts 搜索 UI / 头像同步
- 38 万条骚扰库本体（由你 CDN/对象存储提供）
- 网络实时查号第三方 API
- `cdn.oneims.app` 真传（需 `ONE_CDN_PUT_URL`）