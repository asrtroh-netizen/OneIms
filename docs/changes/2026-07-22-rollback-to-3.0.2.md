# 2026-07-22 · 撤包回退至 OneIms 3.0.2（最终版）

## 做了什么

| 项 | 结果 |
|---|---|
| 工作树 | `git reset --hard afe9e5c`（versionName `3.0.2` / versionCode `72`） |
| GitHub Release | 已删除 `v3.0.4`、`v3.0.3`、`v3.0.2-sandbox-persist`；当前 Latest = **v3.0.2（最终版）** |
| 远程 tag | 仅保留 `v3.0.0` / `v3.0.1` / `v3.0.2`（无 3.0.3/3.0.4） |
| 公开 README | `origin/main` 曾误留 3.0.3 下载链；已前向提交 `51a7dbc` 冻结回 **v3.0.2** 并加最终版说明 |
| 本地重编 | **失败**（现工具链下 `afe9e5c` 编译不过）；下载仍用 2026-07-20 正式包 |

## 公开仓冻结（2026-07-24 补齐）

- 提交：`51a7dbc` · `docs: freeze public README at OneIms 3.0.2 final`
- Release 标题：`OneIms 3.0.2（最终版）`
- 校验：README 无 `releases/download/v3.0.3`、无 `releases/tag/v3.0.3`、无 `*-3.0.3.apk` 下载链
- 策略：公开仓继续只推 README（不用 force-push 抹历史；不用本地源码 `main` 直推 `origin`）

## 用户安装

装过 3.0.3/3.0.4（更高 versionCode）须 **先卸载再装 3.0.2**，否则系统拒绝降级覆盖。

## 下载

https://github.com/asrtroh-netizen/OneIms/releases/tag/v3.0.2
