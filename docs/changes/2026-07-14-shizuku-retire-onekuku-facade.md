# 变更说明：Shizuku 用户面下线 → OneKuku 门面

**日期**：2026-07-14  
**范围**：业务门禁与用户文案；**不删** rikka 依赖与 Provider；不重做页面结构。

## 做了什么

1. 新增 `OneKukuManager`：对外唯一特权门面（`isReady` / `requestActivation` / `isRootChannel`），内部仍委托既有通道。
2. Home / Compat / 一键诊断 / 切卡 / Tile / Broker 用户错误串改为 OneKuku 语义。
3. zh/en 用户可见字符串 purge：不再出现「请先启动 Shizuku」等提示。
4. `publish()` 统一走 `sanitizeUserText`，防止异常栈泄漏通道名。
5. 写门禁保留：未就绪不放行、不假成功。

## 刻意未做

- 删除 gradle `rikka.shizuku` 依赖与 Manifest Provider
- 重写 SystemApiBroker binder 实现
- 引入独立 Magisk/libsu Root 栈（当前 Root = UID0 通道分支）

## 验证

- `compileDebugKotlin` PASS
- 资源值扫描：`<string>` 正文无 `Shizuku`（资源名可暂留）
- 真机写路径 / Root 机 NOT RUN
