# 2026-07-25 · OneCaller 自建举报回灌设计（最省钱）

> 状态：**设计定稿 · 待实现**  
> 约束：用户已拍板最省钱 → **不买**商业查号 API；回灌必须可自托管、可断网降级。

## 1. 目标

| 目标 | 说明 |
|---|---|
| 表层 | 用户把「这是骚扰/诈骗/中介…」标进系统，并最终出现在 **onespam / OneBlock** |
| 深层 | 用社区密度代替买库；本地优先、可选上传 |
| 成功标准 | MVP：本机举报立刻进本地规则/onespam；进阶：多端聚合过门槛后进公开 OneBlock 包 |
| 非目标 | 不做实时商业查号；不做电话邦企业认证；不做来电悬浮 |

## 2. 方案矩阵（已选）

| 方案 | 成本 | 速度 | 选否 |
|---|---|---|---|
| **A. 本机闭环**（举报→本地 onespam/规则，不上云） | ¥0 | 最快 | ✅ **MVP 必做** |
| **B. GitHub 回灌**（客户端导出/定时 POST 到 Issues 或 `reports/` PR 机器人） | ¥0（用现有 GH） | 中 | ✅ **Phase 2** |
| **C. Cloudflare Worker + D1/KV** 聚合 API | 免费额通常够个人/小规模 | 中 | ○ Phase 3 可选 |
| D. 自建 VPS + Postgres | 有月费 | 慢 | ❌ 现阶段不选 |
| E. 接百度 SPNS | 有量包费 | — | ❌ 已否决（最省钱） |

**选定路径：A → B（默认），C 仅当 B 吞吐不够再上。**

## 3. 端到端数据流

```text
┌───────────── App（OneCaller）─────────────┐
│ 来电/通话记录/试查 → 「举报」              │
│   tag: 骚扰|诈骗|中介|推销|快递误标|其他     │
│   note?: 短文本（≤80字）                    │
│   phone: 规范化数字（去 +86）               │
└─────────────┬─────────────────────────────┘
              │ 立刻
              ▼
     LocalReportStore（Room）
              │
      ┌───────┴────────┐
      ▼                ▼
 本机生效（可选自动）   待上传队列（status=pending）
  · CallRule EXACT BLOCK
  · onespam 行 upsert
              │
              │ Phase 2：用户开「参与社区库」且有网
              ▼
     POST /v1/reports  或  导出 JSON 手动/半自动
              │
              ▼
     Aggregator（GH Action 或 Worker）
       · 同号同 tag 计数
       · 过门槛 → candidate
       · 人工/自动 merge → one-blocklist.json
              │
              ▼
     build-onespam-pack.py → Release zip
              │
              ▼
     App「检查云端更新」拉回新 onespam
```

## 4. 契约

### 4.1 本地实体 `local_reports`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string UUID | |
| phone | string | 仅数字，去国家码 |
| tag | enum string | `spam` / `fraud` / `agent` / `sales` / `wrong_tag` / `other` |
| note | string? | ≤80，脱敏、禁粘贴身份证等 |
| source | string | `manual` / `after_call` / `lookup` |
| createdAt | long | epoch ms |
| uploadStatus | enum | `local_only` / `pending` / `uploaded` / `rejected` |
| applyLocal | bool | 是否已写入规则/onespam |

中文展示映射：`spam→骚扰电话`，`fraud→诈骗电话`，`agent→中介`，`sales→广告推销`。

### 4.2 上传体 `onetools.report.v1`（Phase 2）

```json
{
  "schema": "onetools.report.v1",
  "appVersion": "0.1.0-lite",
  "reports": [
    {
      "phone": "17000000003",
      "tag": "agent",
      "noteHash": null,
      "createdAt": 1720000000000,
      "clientId": "opaque-random-uuid"
    }
  ]
}
```

- **不上传** IMEI / GAID / 通讯录 / 完整通话记录  
- `clientId`：安装时随机生成，仅用于反刷，可轮换  
- `note` 默认 **不上传**（或只上传 hash）；防隐私事故  

### 4.3 聚合门槛（建议默认）

| 条件 | 动作 |
|---|---|
| 同 `phone` + 同 `tag` ≥ **3** 个不同 `clientId` | 进入 `candidate` |
| 单 client 24h 上报 > **50** | 忽略后续（反刷） |
| `wrong_tag` ≥ 3 | 从 candidate **降权/移出**（纠错） |
| 白名单号段（10086/银行 LABEL 等） | **禁止**进入 block 候选 |

### 4.4 回灌产物

- 合并进 `docs/product/samples/one-blocklist.json` / OneBlock `phone/one-blocklist.json`  
- 跑 `expand`/`build-onespam-pack` → 更新 Release `spam-sync.json` + zip  
- App 既有「检查云端更新」消化  

## 5. App 交互（MVP）

1. **试查结果页 / 规则列表**：按钮「举报为骚扰」  
2. **拦截/仅提示后**（可选）：通知或 Caller 首页「最近未标记」卡片  
3. 举报 Sheet：选 tag → 确认 → Toast「已加入本机拦截库」  
4. 设置：  
   - 「举报后立即本机拦截」默认 **开**  
   - 「参与社区回灌」默认 **关**（省事+隐私；Phase 2）  

## 6. 文件级实现计划（待开工时）

| 顺序 | 文件 | 职责 |
|---|---|---|
| 1 | `caller/LocalReportStore.kt` + Room entity | 举报持久化 |
| 2 | `caller/ReportApplier.kt` | 写 CallRule + onespam upsert（复用 SpamPackInstaller 单行插入或增量 SQL） |
| 3 | `ui/CallerScreen.kt` | 举报 Sheet + 设置开关 |
| 4 | `caller/ReportExport.kt` | 导出 `onetools.report.v1`（Phase 2 手动） |
| 5 | `onetools/scripts/ingest-reports.py` | 读 reports JSON → 计票 → 改 blocklist（Phase 2） |
| 6 | `.github/workflows/ingest-caller-reports.yml`（可选） | 定时/手动跑 ingest + 开 PR |
| 7 | 文档 + 字符串 + 单测 | 门槛/规范化/白名单 |

**onespam 增量写入注意：** 今日 `SpamPackInstaller.installRows` 是整库替换；MVP 需补 `upsertOne(phone, tag)`，避免举报冲掉整包。

## 7. 安全 / 滥用 / 隐私

- 输入校验：长度、仅数字、拒绝过短（<7 精确；前缀举报另议，MVP 只精确 11 位或 ≥7）  
- 本机可删举报记录并同步移除自动加的规则（按 phone）  
- 社区模式明示：上传号码+标签，自愿  
- 服务端（若有）只存聚合计数，原始报表短期 TTL（如 90 天）  
- 禁止把举报 API 做成「查别人隐私」的公开查询  

## 8. 成本与运维

| 项 | 费用 |
|---|---|
| MVP 本机 | ¥0 |
| GitHub Issues / Actions / Release | ¥0（现有账号） |
| Cloudflare 免费档 | 通常 ¥0（可选） |
| 商业 API | ¥0（不买） |

## 9. 分期与验收

### Phase 1 — MVP（建议下周可做）

- [ ] 举报 → 本地 DB  
- [ ] 立刻 upsert 规则 + onespam 单号  
- [ ] 试查可见「骚扰 · tag」  
- [ ] 可删除举报并撤销  

### Phase 2 — 社区回灌

- [ ] 导出/半自动上传  
- [ ] `ingest-reports.py` + 门槛  
- [ ] PR 到 OneBlock + 重建 zip  

### Phase 3 — 自动同步（可选）

- [ ] Worker 收件 + App 可选上传  
- [ ] 与 `spam-sync` 版本联动  

## 10. 与现有模块关系

| 现有 | 关系 |
|---|---|
| `OneBlockImporter` / `SpamSyncRepository` | 消费回灌产物 |
| `CallerCheckEngine` | 无需改主流程；本地 onespam 命中即可 |
| `CallerPrefs.noNetworkQuery=true` | 保持；回灌 **不是** 实时查号 |
| Telo feedbackToken | 可借鉴「通话后询问」UX，干净室自研，不抄代码 |

## 11. 建议拍板点（实现前）

1. Phase 1 是否 **自动** 本机拦截（推荐：是）  
2. 社区回灌默认开还是关（推荐：**关**）  
3. 聚合门槛 3 是否接受（可调）  

---

**设计结论：** 最省钱回灌 = **本机立刻生效 + GitHub/脚本聚合进 OneBlock**；不引入付费 API，不绑 Telo 后端。  
