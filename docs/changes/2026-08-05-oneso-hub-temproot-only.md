# OneSo Hub：只留单窗一键临时 Root

## 变更

- Hub UI **去掉** pack-0705 / pack-p10 / 打开 Tk；文案明确 so 工厂在 GitHub。
- 单实例锁：`%TEMP%\oneso-hub-single.lock`，避免多开。
- `scripts/oneso-hub.ps1` 启动前清残留 hub/gui 进程与失效锁，再开一扇。
- CLI `pack-*` 仍保留给 GitHub/assets 维护，不在 Hub 暴露。
