# 2026-08-03 · 经 FNHOME 进入上级小米路由后台

## 结论

HaloXFN（FNHOME）上级网关 `192.168.2.1` 是 **Xiaomi 路由器 BE6500**（硬件 RN02，路由名「客厅」，ROM 1.0.43）。后台 Web **可达**。

## 入口

| 场景 | 做法 |
|---|---|
| 人在家里同一 WiFi | 浏览器打开 `http://192.168.2.1/` |
| 人在外 / 办公室 | 经 FNHOME SSH 本地隧道（已开 `AllowTcpForwarding`）→ 本机 `http://127.0.0.1:18080/` |

登录密码：小米路由管理密码（机身贴纸 / 米家 App / 当初自设），**本轮未持有、未爆破**。

## 本轮改动

- 飞牛默认 ` /etc/ssh/sshd_config.d/trim_sshd.conf` 原为 `AllowTcpForwarding no`，已改为 `yes`（备份 `trim_sshd.conf.bak-before-tcpforward`），以便 SSH `direct-tcpip` 隧道。
- 办公室侧可用 Host 改写隧道脚本把 `127.0.0.1:18080` 转到路由（小米 nginx 拒绝 `Host: 127.0.0.1` 会 502）。

## 验证

| 检查 | 结果 |
|---|---|
| HaloXFN → `192.168.2.1` ping | 通 |
| `http://192.168.2.1/` 标题 | 小米路由器 |
| `init_info` | displayName=Xiaomi路由器BE6500 / routername=客厅 |
| 本机 `http://127.0.0.1:18080/`（改写 Host 后） | 标题小米路由；init_info JSON 正常 |
| 路由登录是否成功 | **NOT RUN**（缺管理密码，需浏览器人工登录） |
