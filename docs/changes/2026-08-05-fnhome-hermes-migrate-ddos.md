# 2026-08-05 · FNHOME Hermes（爱马仕）迁到办公室 DDOS

## 纠正

上一会话把「爱马仕」误认成 AnGe-Panel。用户明确纠正为 **Hermes**。

| 侧 | 应用包 | 版本 | 说明 |
|---|---|---|---|
| FNHOME（`hfs.itt.fan:1818` / HaloXFN） | `hermes-agent`（第三方 fpk，distributor=bbis/iranee） | **v0.19.0-50** | dashboard `127.0.0.1:9119`，gateway `:8642` |
| DDOS（`192.168.1.99`） | `trim.hermes`（应用中心「Hermes」） | **0.18.0-1** | dashboard `127.0.0.1:19119`，入口 `/app/trim-hermes/` |

二者都是 Nous Research Hermes Agent 生态，但 **包名/发行渠道不同**，不能当成 AnGe-Panel。

## 已完成

1. **定位**：SSH 双端确认 FNHOME 有 `/var/apps/hermes-agent` 与 fpk；DDOS 已有运行中的 `trim.hermes`。
2. **中转制品**：
   - `/vol1/1000/fnos-hermes-agent_v0.19.0-50.fpk`（18M，供应用中心手动上传）
   - `/vol1/1000/hermes-migrate/`（fpk、数据包、trim-cli、备份）
3. **数据迁移**（注入 DDOS `trim.hermes` 数据根 `/vol1/@appdata/trim.hermes/hermes/`）：
   - `config.yaml` / `.env` / `.env.providers` / `providers-state.yaml`
   - `chat/`、`sessions/`、`state.db`、`auth.json`、`skills/`、`cron/`、`SOUL.md`、`AGENTS.md` 等
   - 迁移前备份：`/vol1/1000/hermes-migrate/backup-20260805-024154/`
4. **服务**：`trim-cli app stop/start trim.hermes --yes` 已执行；进程 `trim-hermes-wrapper` 已重新拉起。

## 未完成 / 阻塞

- **CLI 无法静默安装 `hermes-agent` fpk**：`trim-cli app install-fpk` 返回  
  `app-center install requires license confirmation; install it from App Center UI`。  
  需要你在 **DDOS 飞牛应用中心 → 手动上传**  
  `/vol1/1000/fnos-hermes-agent_v0.19.0-50.fpk` **并确认许可**，才能装上与 FNHOME 同包名的 `hermes-agent` 0.19。
- 当前运行态仍是 **`trim.hermes` 0.18.0-1** + 已迁入的 FNHOME 配置/会话数据（跨包兼容，以面板内模型/对话是否正常为准）。

## 验收（本轮）

| 检查 | 结果 |
|---|---|
| 数据文件 `config.yaml` / `.env` / `providers-state.yaml` / `chat` | 已写入 `/vol1/@appdata/trim.hermes/hermes/`（含 `custom-wkapi` / `kimi-k3`） |
| `.env` 键（脱敏） | `CUSTOM_WKAPI_API_KEY` / `QQ_*` / `API_SERVER_*` |
| FNHOME 会话包 | 暂存 `/vol1/1000/hermes-migrate/fnhome-sessions-sidecar/`（0.19→0.18 直接覆盖 `state.db` 会导致 dashboard 起不来，故未强塞进运行库） |
| dashboard `:19119` | 修复 `.env` ACL（曾被掏空为 `---`）后可 `200`；稳定拉起用 wrapper + 必要时 `hermes_cli.main dashboard` |
| `hermes-agent` 0.19 同包安装 | **BLOCKED**：需应用中心 UI 确认许可；fpk 已放 `/vol1/1000/fnos-hermes-agent_v0.19.0-50.fpk` |

### 事故与修复

1. 拷贝文件后 `.env` ACL 变成 `user::---` → wrapper 报 `permission denied` → `setfacl -b` + `chmod 600` 修复。  
2. 把 FNHOME 0.19 的 `state.db` 直接盖到 0.18 上会导致 dashboard 子进程不起 → 已恢复备份 `state.db`，会话改放 sidecar。

## 回滚

```bash
# 在 DDOS 上
trim-cli --host 127.0.0.1 --port 9999 login   # 管理员
# 停服务后还原备份目录到 /vol1/@appdata/trim.hermes
```

备份路径：`/vol1/1000/hermes-migrate/backup-20260805-024154/`。

## 建议下一步

1. 浏览器打开 DDOS 飞牛 → 应用「Hermes」→ 确认供应商/对话是否已是家侧那套。
2. 若必须与 FNHOME **同包**（`hermes-agent` 0.19 UI/monitor）：上传上述 fpk 并点许可安装；装完后再把 `hermes-migrate/from-fnhome` 迁入新包数据目录。
3. 确认无误后，再决定是否停用 FNHOME 侧 `hermes-agent`（本轮未停源站）。
