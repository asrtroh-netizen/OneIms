# 2026-08-03 · 单位飞牛 DDOS 试点安装 Tailscale

## 目标

在办公室飞牛 `192.168.1.99`（hostname `DDOS`）安装 Tailscale，与现网阿里云 NPS **共存**试点。

## 已完成

| 项 | 结果 |
|---|---|
| 系统 | Debian 12 bookworm / x86_64 / kernel trim |
| 安装 | 官方源 `tailscale 1.98.10` + `tailscaled` **active** |
| hostname 参数 | `office-feiniu-ddos`（授权后生效） |
| `--accept-dns` | **false**（避免改办公室 DNS） |
| 登录 | 需浏览器打开授权链接（见交付说明） |

## 验证

| 检查 | 结果 |
|---|---|
| `tailscaled` active | **PASS** |
| `tailscale ip -4` | **NeedsLogin**（等授权） |
| 办公室 PC / 其它节点互通 | **NOT RUN** |

## 回滚

```bash
sudo tailscale down
sudo systemctl disable --now tailscaled
sudo apt remove -y tailscale
```
