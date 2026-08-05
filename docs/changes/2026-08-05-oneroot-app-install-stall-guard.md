# OneRoot App：安装页「一直转」停顿守护（1.1.1）

## 现象

手机端进入「获取临时 ROOT」后长时间转圈、无法回到可操作态；设备日志显示 native payload 卡在 `prepare_kernel_page mode=0`，进程仍存活，UI 无取消入口。

## 根因

1. `InstallViewModel` 停顿超时原先为 **600s**、总超时 **1800s**，无新日志也会长时间转圈。
2. 忙碌态 `BackHandler` 吞掉返回键，且无「取消」按钮，无法中止。
3. 超时失败路径未 `kill()` native 进程，易残留 `exploit_service`。
4. （顺带）主页 `Shizuku.getUid() == 2000` 硬条件会让 root 启动的 Shizuku 卡在「激活中」。

## 改动（fork：`E:\GQ\One\_forks\OneRoot`）

| 项 | 内容 |
|---|---|
| 版本 | `1.1.1` / `versionCode 12` |
| 停顿超时 | 90s 无新日志 → 杀进程并失败 |
| 总超时 | 8 分钟 |
| UI | 忙碌态显示「取消」；返回键触发取消；状态文案显示已用秒数 / 无新日志秒数 |
| 清理 | 启动前 `unbindUserService(..., remove=true)`；失败/取消路径 `kill()` |
| Shizuku | 主页/安装侧接受 uid `2000` 或 `0`；主页 refresh 保留既有匹配信息 |

## 验证

- `assembleDebug`：**BUILD SUCCESSFUL**
- 设备安装：`versionName=1.1.1` / `versionCode=12`
- `aapt dump resources`：含 `action_cancel_install` / `status_exploit_progress` / `error_exploit_stalled`
- Fold 双屏 + 前台焦点被其它 App 抢走时，完整点「取消」的 E2E：**NOT RUN**（需人工在外屏前台点一次安装→取消）

## 说明

本改动治的是「假死 UI / 无限转圈」体验与可中止性；**不能保证** CVE payload 在 `prepare_kernel_page` 一定成功。payload 本身卡死仍需 so / 环境侧排查。
