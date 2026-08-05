# OneRoot 完全体 · 详细诊断日志模块（2026.08.05.1）

## 动机

他机远程排障时，Hub 前端/内存日志会被截断（约 8k～12k），对方说不清失败点。需要会话级落盘 + 一键导出 zip。

## 交付

- 新增 `OneRoot/diaglog.py`：`SessionLogger`（session 目录、级别日志、meta 脱敏、export zip、smoke）
- `hub.py`：启动建会话；任务 print 镜像落盘；`GET /api/diag`、`POST /api/diag/export`、`POST /api/diag/open`；版本 `2026.08.05.1`
- UI：`导出诊断日志` 按钮；版本芯片显示 `v2026.08.05.1`
- `本地完全体说明.txt` / `.gitignore`（忽略 `logs/`）

## 验证

```text
cd OneRoot
python diaglog.py smoke   # ok=true，token 脱敏
python -m py_compile diaglog.py hub.py
```
