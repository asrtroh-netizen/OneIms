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

## 2026-08-05 续 · WKAPI 核对（已完成）

截图报错 `dashboard port 127.0.0.1:19119 ... address already in use`：**不是 WK 挂了**，而是已有 dashboard 占着 19119，UI 再点启动/Retry 会二次 bind 失败。

| 检查 | 结果（本轮 SSH 证据） |
|---|---|
| 冲突根因 | 旧 wrapper/dashboard（约运行 8h）占 `127.0.0.1:19119`，`dash_http=200` |
| 收敛 | 杀旧进程 → 起 wrapper；wrapper 未自动 spawn dashboard → fallback `hermes_cli.main dashboard` → **200** |
| 配置 | `provider=custom-wkapi`，`default=kimi-k3`，`base_url=https://wkapi.vip/v1`，`.env` 有 `CUSTOM_WKAPI_API_KEY` |
| WKAPI `/models` | **200**，`model_ids=['kimi-k3']` |
| 对话探针 | `hermes -z "Reply with exactly: WK_OK" --provider custom-wkapi -m kimi-k3` → 输出 **`WK_OK`**，exit 0 |
| `api_server :18642` | 未监听（本轮未强制拉起；对话链路不依赖它） |

说明：飞牛应用入口再点「打开」若仍报端口占用，是因为 dashboard 已在跑——应直接用已运行实例，或先停再开；`status` 里官方 API Key 槽位多为空属正常，WK 走 custom provider。

### 启动报错续查（同日）

| 项 | 结论 |
|---|---|
| 正确入口 | fnOS iframe：`/app/trim-hermes/`（`ui/config` → `gatewaySocket=run/trim-hermes.sock`） |
| 官方 start | 必须带 `TRIM_APPDEST=/vol1/@appcenter/trim.hermes` 等；裸跑 `cmd/main start` 会变成 `missing wrapper binary: /wrapper/...` |
| wrapper | 经 socket 访问时**会自动 spawn** dashboard（已实测：杀 dashboard 后 sock 一打即起） |
| 截图 Retry | dashboard 已 `200` 时再 `hermes dashboard` → 精确复现 `address already in use`（exit 1）；**点 Retry 会自己制造报错** |
| 用法 | 管理员登录飞牛 → 打开 Hermes；见该错时**关掉窗口勿 Retry**，刷新/重开入口即可 |

## 2026-08-05 续 · 更新 Hermes Agent 至 0.19.0

| 路径 | 结果 |
|---|---|
| `install-fpk` 同包 `hermes-agent` 0.19 | **仍 BLOCKED**：`license confirmation`（需应用中心 UI） |
| 云端 `app update trim.hermes` | 下载失败 code `-6` |
| 应用内 `uv pip install --python …/python3.11.real --upgrade hermes-agent` | **成功**：`0.18.0 → 0.19.0`（包外壳仍显示 `trim.hermes 0.18.0-1`） |
| `hermes config migrate` | 配置版本 `v0 → v33` |
| 服务 | 官方 env start；dashboard `200`；`status=running` |
| WK 复验 | 升级后遇 `wkapi.vip`：**路由器 DNS 给 NXDOMAIN**；写 `/etc/hosts` 后 **TLS/连接被重置**；办公网 Windows 同步超时——属外网/CDN，非包升级逻辑回归。升级前同日曾 `WK_OK`。 |

回滚 agent 包（若需）：在 DDOS 上对应用 Python 执行  
`…/python3.11.real -m pip install 'hermes-agent==0.18.0'`（或从备份恢复 site-packages）。

### 正式 fpk `hermes-agent` 0.19（待 UI 许可）

| 项 | 状态 |
|---|---|
| CLI `install-fpk --yes` | **BLOCKED**：`license confirmation`（无法 CLI 代点） |
| 安装包位置 | `/vol1/1000/app-packages/fnos-hermes-agent_v0.19.0-50.fpk` 与 `/vol1/1000/应用安装包/` |
| 装完后迁配置 | `/vol1/1000/hermes-migrate/post-fpk-migrate.sh`（停 trim.hermes、迁 WK config） |

### 正式包已装（用户 UI 许可后）

| 项 | 结果 |
|---|---|
| 应用 | `hermes-agent` **0.19.0-50** 已出现在应用中心；`trim.hermes` 已被卸掉 |
| 配置 | 从 `hermes-migrate/pre-update-*` / `from-fnhome` 灌入 `@appdata/.../hermes` 与 `@apphome/.../data`（含 `custom-wkapi`） |
| 启动 | App Center start 仍报依赖 `nodejs_v24` 需 UI；已用自带 node + `MONITOR_SOCKET_PATH` 拉起 `monitor.js` |
| 入口 | `https://192.168.1.99/app/hermes-agent`（unix sock **200**） |
| 已知坑 | `dashboard.js` 曾有 `` `,</style>` `` 语法损坏（已补丁）；monitor 首次启动会 “Config reset”，需再写回 WK 配置 |
| 权限坑 | 备份里 `config.yaml`/`providers-state.yaml` 曾是 mode `000`；`cp -a` 后 Hermes 报 Permission denied 并回落默认配置、忽略 `custom-wkapi`。迁移后必须 `chmod 640` + `chown hermes-agent` |
| AI 模型（昨天契约） | `provider=custom-wkapi` / `default=kimi-k3` / `base_url=https://wkapi.vip/v1` / `.env`→`CUSTOM_WKAPI_API_KEY`；`hermes status` 已确认 Model/Provider。WK 实聊依赖外网 TLS，当前可能 Connection error |
| 面板启动门禁 | `/api/start` 读 `@appdata/hermes-agent/providers-state.yaml`（不是 hermes 子目录）；缺则报「请先添加模型服务商」。需同时有 `.env.providers` |
| 启动 500 | `gateway.log` 若属 root → monitor(EACCES)。日志目录须 `chown hermes-agent` |
| 端口契约 | 正式包 Gateway **8642** / Dashboard **9119**；旧 trim `.env` 的 `API_SERVER_PORT=18642` 会让 Gateway 绑错口，健康检查变红。已改回 8642 |
| 官方启动结果 | `POST /api/start` → gateway+dashboard+bridge ok；`api/status` 双绿；`proxy/dashboard` **200** |

### QQ 从 FNHOME 迁到 DDOS（同日）

| 项 | 结果 |
|---|---|
| 源 | FNHOME `hermes-agent`：停 `gateway`；`.env` 中 `QQ_*` 注释并脱敏；`platforms.qqbot.enabled=false` |
| 目标 | DDOS：合并同源 `QQ_*`（sha 对齐）、`qqbot.enabled=true` + platforms 目录；`POST /api/start` 后 Gateway/Dashboard **healthy** |
| 备份 | FNHOME：`/vol1/1000/hermes-migrate/qq-disable-*`；DDOS：`qq-import-*` |
| 验收 | FNHOME `NO_GATEWAY`；DDOS gateway PID 在跑、端口 8642/9119；QQ 实聊需在 QQ 侧人工确认（本轮 NOT RUN） |
